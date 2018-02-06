definition(
    name: 'Lutron HomeWorks',
    namespace: 'crazyjncsu',
    author: 'Jake Morgan',
    description: 'Lutron HomeWorks integration via native processor-embedded web server',
    category: 'Convenience',
    iconUrl: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png',
    iconX2Url: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png',
    iconX3Url: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png'
)

preferences {
    input 'processorAuthority', 'text', title: 'Processor', description: 'Processor endpoint in format: host[:port]', defaultValue: '192.168.1.86:80', required: true, displayDuringSetup: true
    input 'deviceNameFormat', 'text', title: 'Device Name Format', description: 'Format of device names when added; %1$s=keypad; %2$03d=number; %3$s=engraving', defaultValue: '%1$s %3$s', required: true, displayDuringSetup: true
    input 'keypadFilterExpression', 'text', title: 'Keypad Filter Expression', description: 'Optional regex to filter keypads', defaultValue: '', required: false, displayDuringSetup: true
    input 'buttonFilterExpression', 'text', title: 'Button Filter Expression', description: 'Optional regex to filter buttons', defaultValue: '', required: false, displayDuringSetup: true
    input 'devicePollIntervalSeconds', 'number', title: 'Device Poll Interval in Seconds', description: 'Device poll interval in seconds', defaultValue: '3600', required: false, displayDuringSetup: true
    input 'statusPollIntervalSeconds', 'number', title: 'Status Poll Interval in Seconds', description: 'Status poll interval in seconds', defaultValue: '5', required: false, displayDuringSetup: true
}

def installed() {
    initialize();
}

def updated() {
	unschedule();
    unsubscribe();
    initialize();
}

def uninstalled() {
    getChildDevices().each { deleteChildDevice(it.deviceNetworkId); }
}

def initialize() {
    runSyncDevicesLoop();
    runSyncStatusLoop();
}

def runSyncDevicesLoop() {
    runIn(devicePollIntervalSeconds, runSyncDevicesLoop);
    sendLutronHttpGets([[fileBaseName: 'keypads', queryStringMap: [sync:'1']]]);
}

def runSyncStatusLoop() {
    runIn(statusPollIntervalSeconds, runSyncStatusLoop);
    sendLutronHttpGets(getAllChildDevices().collect { it.deviceNetworkId.split(':')[0] }.unique().collect { [fileBaseName:'leds', queryStringMap: [keypad: it]] });
}

def sendLutronHttpGets(requestInfos) {
    def hubActions = requestInfos.collect {
        def hubAction = new physicalgraph.device.HubAction(
            [
                method: 'GET',
                path: "/${it.fileBaseName}.xml",
                query: it.queryStringMap,
                headers: [ Host: processorAuthority ],
            ],
            null,
            [ callback: handleLutronHttpResponse ]
        );

        hubAction.requestId = it.queryStringMap.collect { "${URLEncoder.encode(it.key)}=${URLEncoder.encode(it.value)}" }.join('&');
        
        log.info("sendLutronHttpGet: ${hubAction.toString().split('\n')[0]}");

        return hubAction;
    }

    sendHubCommand(hubActions, 400);
}

def handleLutronHttpResponse(physicalgraph.device.HubResponse response) {
    def requestQueryStringMap = response.requestId.split('&').collect { it.split('=') }.collectEntries { [(URLDecoder.decode(it[0])): URLDecoder.decode(it[1])] };

    switch (response.xml?.name()) {
        case 'Project':
            def matchingKeypadNodes = response.xml?.HWKeypad?.findAll { keypadFilterExpression == null || it.Name.text() =~ keypadFilterExpression };
            def keypadAddressSet = matchingKeypadNodes.collect { it.Address.text() }.toSet();
        
            getAllChildDevices().findAll { !keypadAddressSet.contains(it.deviceNetworkId.split(':')[0]) }.each { deleteChildDevice(it.deviceNetworkId) };
            
            sendLutronHttpGets(matchingKeypadNodes.collect { [fileBaseName: 'buttons', queryStringMap: [keypad: it.Address.text(), name: it.Name.text()]] });
            
            break;
        case 'List':
            def matchingButtonNodes = response.xml?.Button?.findAll { it.Type.text() == 'LED' && it.Engraving.text() }.findAll { buttonFilterExpression == null || it.Engraving.text() =~ buttonFilterExpression };
            def buttonNumberSet = matchingButtonNodes.collect { it.Number.text() }.toSet();

            getAllChildDevices().findAll { it.deviceNetworkId.split(':')[0] == requestQueryStringMap.keypad && !buttonNumberSet.contains(it.deviceNetworkId.split(':')[1]) }.each { deleteChildDevice(it.deviceNetworkId) };
            
            matchingButtonNodes.each {
                def deviceNetworkId = requestQueryStringMap.keypad + ':' + it.Number.text();
                def deviceName = String.format(deviceNameFormat, requestQueryStringMap.name, it.Number.text().toInteger(), it.Engraving.text());
                def existingChildDevice = getChildDevice(deviceNetworkId);
                def nameChanged = existingChildDevice != null && !existingChildDevice.name.equals(deviceName);

                if (nameChanged)
                    deleteChildDevice(deviceNetworkId);

                if (existingChildDevice == null || nameChanged)
                    addChildDevice('erocm123', 'Switch Child Device', deviceNetworkId, null, [name: deviceName, label: deviceName]);
            }
        
            if (matchingButtonNodes.size() != 0)
                sendLutronHttpGets([[fileBaseName:'leds', queryStringMap: requestQueryStringMap]]);    

            break;
        case 'LED':
            def ledsString = response.xml?.LEDs.text();
            
            getAllChildDevices().findAll { it.deviceNetworkId.split(':')[0] == requestQueryStringMap.keypad }.each {
                def buttonNumberString = it.deviceNetworkId.split(':')[1];
                def currentState = ledsString.charAt(buttonNumberString.toInteger()) == '1' ? 'on' : 'off';
                    
                it.sendEvent(name: 'switch', value: currentState);
                
                if (buttonNumberString == requestQueryStringMap.button && requestQueryStringMap.state != 'unspecified') {
                    def attempts = requestQueryStringMap.attempts ? requestQueryStringMap.attempts.toInteger() : 0;
                    log.info("keypad: ${requestQueryStringMap.keypad}; button: $buttonNumberString; currentState: $currentState; desiredState: ${requestQueryStringMap.state}; attempts: $attempts");
                    attempts++;

                    if (currentState != requestQueryStringMap.state && attempts < 5)
                        sendLutronHttpGets([
                            [fileBaseName:'action', queryStringMap: [keypad: requestQueryStringMap.keypad, button: buttonNumberString, action: 'press']],    
                            [fileBaseName:'action', queryStringMap: [keypad: requestQueryStringMap.keypad, button: buttonNumberString, action: 'release']],
                            [fileBaseName:'leds', queryStringMap: [keypad: requestQueryStringMap.keypad, button: requestQueryStringMap.button, state: requestQueryStringMap.state, attempts: attempts.toString() ]],
                        ]);
                }
            }
            
            break;
        case 'Action':
            // don't care here, as an leds was always issued after this one
            break;
    }
}

def childOn(childDeviceNetworkId) {
    setChildDeviceState(childDeviceNetworkId, 'on');
}

def childOff(childDeviceNetworkId) {
    setChildDeviceState(childDeviceNetworkId, 'off');
}

def childRefresh(childDeviceNetworkId) {
    setChildDeviceState(childDeviceNetworkId, 'unspecified');
}

def setChildDeviceState(childDeviceNetworkId, state) {
    // check that the state of the leds to make sure we even have to press the button to achieve the desired state
    sendLutronHttpGets([[fileBaseName: 'leds', queryStringMap: [keypad: childDeviceNetworkId.split(':')[0], button: childDeviceNetworkId.split(':')[1], state: state]]]);    
}
