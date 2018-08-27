definition(
        name: "AutoBridge",
        namespace: "autobridge",
        author: "autobridge",
        description: "AutoBridge connector for SmartThings",
        category: "Convenience",
        iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
        iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
        iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png"
)

preferences {
    section {
        input "actuators", "capability.actuator", multiple: true
        input "sensors", "capability.sensor", multiple: true
    }
}

def installed() {
    initialize()
}

def updated() {
    unsubscribe()
    initialize()
}

def uninstalled() {
    getChildDevices().each { deleteChildDevice(it.deviceNetworkId) }
}

def initialize() {
    subscribe(location, null, processLocationEvent, [filterEvents: false])

	sensors
    	.plus(actuators)
        .each { device ->
        	device.getCapabilities()
            	.collectMany { it.getAttributes() }
                .each { subscribe(device, it.name, processDeviceEvent) }        	
        }

    searchForDevices()
    runEvery30Minutes(searchForDevices)
}

def searchForDevices() {
    log.info "Searching for devices..."
    sendHubCommand(new physicalgraph.device.HubAction("lan discovery urn:schemas-upnp-org:device:AutoBridge:1", physicalgraph.device.Protocol.LAN))
}

def parseDniPath(deviceNetworkId) {
    def dniParts = deviceNetworkId.split(':')
    return [targetContainerID: dniParts[0], sourceContainerID: dniParts[1], deviceID: dniParts[2]]
}

def getChildDeviceInfos() {
    return getAllChildDevices().collect { [device: it, path: parseDniPath(it.deviceNetworkId)] }
}

def getAssignedDevice(deviceNetworkId) {
    return actuators.find { it.deviceNetworkId == deviceNetworkId } ?: sensors.find { it.deviceNetworkId == deviceNetworkId }
}

def getSubscribeEventsMap() {
    if (state.subscribeEventsMap == null) state.subscribeEventsMap = [:]
    return state.subscribeEventsMap
}

def getContainerHostMap() {
    if (state.containerHostMap == null) state.containerHostMap = [:]
    return state.containerHostMap
}

def getContainerValidationKeyMap() {
    if (state.containerValidationKeyMap == null) state.containerValidationKeyMap = [:]
    return state.containerValidationKeyMap
}

def getPropertyNameValueMap(device) {
    device.getSupportedAttributes().collectEntries { [(it.name): device.currentValue(it.name)] }
}

def tryDeleteChildDevice(deviceNetworkId) {
	try {
    	// TODO fix
		deleteChildDevice(it.device.deviceNetworkId)
    } catch (ex) {
    	log.error("Error deleting child device '${deviceNetworkId}': ${ex}")
    }
}

def processDeviceEvent(event) {
	getSubscribeEventsMap()
            .collect()
            .findAll { it.value > new Date().getTime() }
            .each { log.info("Sending event to '${it.key}' from '${event.device.name}'; '${event.name}': '${event.value}'") }
            .each { sendMessage(it.key, [deviceID: event.device.deviceNetworkId, propertyName: event.name, propertyValue: event.value]) }
}

def processLocationEvent(event) {
    def lanMessage = parseLanMessage(event.description)

    if (lanMessage?.ssdpTerm == "urn:schemas-upnp-org:device:AutoBridge:1") {
        log.info("Processing SSDP message from ${lanMessage.networkAddress}...")

        def containerID = lanMessage.ssdpUSN.split(':')[1]
        getContainerHostMap()[containerID] = lanMessage.networkAddress
    } else if (lanMessage?.json?.autoBridgeOperation == "setValidationKey") {
    	// TODO implement
        getContainerValidationKeyMap()
    } else if (lanMessage?.json?.autoBridgeOperation == "syncSources") {
        def sourceIDs = lanMessage.json.sourceIDs.toSet()

        log.info("Syncing device sources from '${lanMessage.json.containerID ?: lanMessage.json.targetID}' for ${sourceIDs.size()} sources...")

        getChildDeviceInfos()
                .findAll { it.path.targetContainerID == (lanMessage.json.containerID ?: lanMessage.json.targetID) }
                .findAll { !sourceIDs.contains(it.path.sourceContainerID) }
                .each { tryDeleteChildDevice(it.device.deviceNetworkId) }

        searchForDevices()
    } else if (lanMessage?.json?.autoBridgeOperation == "syncSourceDevices") {
        def existingDeviceIDChildDeviceMap = getChildDeviceInfos()
                .findAll { it.path.targetContainerID == (lanMessage.json.containerID ?: lanMessage.json.targetID) && it.path.sourceContainerID == lanMessage.json.sourceID }
                .collectEntries { [(it.path.deviceID): it.device] }

        def deviceIDs = lanMessage.json.devices.collect { it.deviceID }.toSet()

        log.info("Syncing devices from '${lanMessage.json.containerID ?: lanMessage.json.targetID}' for ${deviceIDs.size()} devices...")

        existingDeviceIDChildDeviceMap
                .findAll { !deviceIDs.contains(it.key) }
                .each { tryDeleteChildDevice(it.value.deviceNetworkId) }

        lanMessage.json.devices.each {
            def existingChildDevice = existingDeviceIDChildDeviceMap[it.deviceID]

            if (existingChildDevice == null) {
                addChildDevice(it.namespace, it.typeName, (lanMessage.json.containerID ?: lanMessage.json.targetID) + ':' + lanMessage.json.sourceID + ':' + it.deviceID, null, [name: it.name, label: it.name])
            } else if (existingChildDevice.name != it.name) {
                if (existingChildDevice.name == existingChildDevice.label)
                    existingChildDevice.label = it.name

                existingChildDevice.name = it.name
            }
        }
    } else if (lanMessage?.json?.autoBridgeOperation == "syncDeviceState") {
        def childDevice = getChildDevice((lanMessage.json.containerID ?: lanMessage.json.targetID) + ':' + lanMessage.json.sourceID + ':' + lanMessage.json.deviceID);

        log.info("Syncing state from '${lanMessage.json.containerID ?: lanMessage.json.targetID}' for device '${lanMessage.json.deviceID}' (${childDevice?.name}); setting property '${lanMessage.json.propertyName}' to '${lanMessage.json.propertyValue}'...")

        if (lanMessage.json.propertyName == "image" && lanMessage.json.propertyValue != "")
            childDevice?.storeImage(
                    java.util.UUID.randomUUID().toString().replaceAll('-', ''),
                    new ByteArrayInputStream(lanMessage.json.propertyValue.decodeBase64())
            )
        else
            childDevice?.sendEvent(name: lanMessage.json.propertyName, value: lanMessage.json.propertyValue)

        // doesn't seem to care if some handlers don't implement this
        childDevice?.onEventSent(lanMessage.json.propertyName, lanMessage.json.propertyValue);
    } else if (lanMessage?.json?.autoBridgeOperation == "getDevices") {
        log.info("Getting devices for '${lanMessage.json.containerID}'...")

        searchForDevices()

        sendMessage(
                lanMessage.json.containerID,
                [
                        devices: actuators
                                .plus(sensors)
                                .toSet()
                                .collect
                                {
                                    [
                                            deviceID: it.deviceNetworkId,
                                            name: it.label ?: it.name,
                                            capabilityNames: it.getCapabilities().collect { it.name }.toList(),
                                            propertyNameValueMap: getPropertyNameValueMap(it)
                                    ]
                                }
                ]
        )
    } else if (lanMessage?.json?.autoBridgeOperation == "getDeviceState") {
        log.info("Getting device state for '${lanMessage.json.containerID}' for device '${lanMessage.json.deviceID}'...")

        def assignedDevice = getAssignedDevice(lanMessage.json.deviceID)

        if (assignedDevice)
            sendMessage(
                    lanMessage.json.containerID,
                    [
                            deviceID: lanMessage.json.deviceID,
                            propertyNameValueMap: getPropertyNameValueMap(assignedDevice)
                    ]
            )
    } else if (lanMessage?.json?.autoBridgeOperation == "invokeDeviceCommand") {
        log.info("Invoking device command for '${lanMessage.json.containerID}' for device '${lanMessage.json.deviceID}', command '${lanMessage.json.commandName}'...")
        getAssignedDevice(lanMessage.json.deviceID)?."${lanMessage.json.commandName}"()
    } else if (lanMessage?.json?.autoBridgeOperation == "subscribeEvents") {
        log.info("Setting subscription for'${lanMessage.json.containerID}'...")
        getSubscribeEventsMap()[lanMessage.json.containerID] = new Date(new Date().getTime() + lanMessage.json.expirationMillisecondCount).getTime()
    } else {
        // NOOP?
        //log.info("Unhandled event: ${lanMessage}")
    }
}

def requestChildStateChange(childDeviceNetworkId, propertyName, propertyValue) {
    log.info("Requesting state change for '$childDeviceNetworkId' for property '$propertyName' to '$propertyValue'...")

    def dniPath = parseDniPath(childDeviceNetworkId)
    sendMessage(dniPath.targetContainerID, [sourceID: dniPath.sourceContainerID, deviceID: dniPath.deviceID, propertyName: propertyName, propertyValue: propertyValue])
}

def requestChildRefresh(childDeviceNetworkId) {
    log.info("Requesting refresh for '$childDeviceNetworkId'...")

    def dniPath = parseDniPath(childDeviceNetworkId)
    sendMessage(dniPath.targetContainerID, [sourceID: dniPath.sourceContainerID, deviceID: dniPath.deviceID])
}

def computeSignatureString(validationKey, dateString, bodyString) {
    if (validationKey == null)
        return "";

    def hmacAlgorithm = javax.crypto.Mac.getInstance("HmacSHA256")
    hmacAlgorithm.init(new javax.crypto.spec.SecretKeySpec(validationKey, "HmacSHA256"))

    def signatureSource = "$dateString$bodyString".getBytes("UTF-8")
    def signatureBytes = hmacAlgorithm.doFinal(signatureSource)

    return org.apache.commons.codec.binary.Base64.encodeBase64String(signatureBytes)
}

def sendMessage(containerID, message) {
    def dateString = new Date().format("EEE, dd MMM yyyy HH:mm:ss z", TimeZone.getTimeZone("UTC"))

    def bodyString = new groovy.json.JsonBuilder([
            targetID: containerID,
            containerID: containerID,
            message: message,
    ]).toString()
    
    def host = getContainerHostMap()[containerID] + ":040B" // port 1035
    
    //log.info("Sending message to '${host}': ${bodyString}")

    sendHubCommand(new physicalgraph.device.HubAction([
            method: 'POST',
            path: "/container/" + containerID,
            headers: [
                    Host: host,
                    Date: dateString,
                    Authorization: computeSignatureString(getContainerValidationKeyMap()[containerID], dateString, bodyString),
            ],
            body: bodyString
    ]))
}
