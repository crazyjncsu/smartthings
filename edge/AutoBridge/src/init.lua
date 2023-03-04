local Driver = require "st.driver"
local Device = require "st.device"
local luxure = require "luxure"
local capabilities = require "st.capabilities"
local log = require "log"
local socket = require "cosock.socket"
local cosock = require "cosock"
local http = cosock.asyncify "socket.http"
local ltn12 = require "ltn12"
local utils = require "st.utils"
local json = require "st.json"
local sha1 = require "sha1"
local base64 = require "st.base64"
local url = require "socket.url"

local attributeIDToCapabilityIDTable = {
    switch = "switch",
    sound = "soundSensor",
    door = "doorControl",
    contact = "contactSensor",
    motion = "motionSensor",
    alarm = "alarm"
}

local webServer = luxure.Server.new()

local driver = Driver("AutoBridge", {
    webServer = webServer,
    discovery = function(driver, _, should_continue)
        while should_continue() do
            searchForBridges(function(_, targetBridgeDeviceNetworkID, bridgeName)
                if not driver:get_device_by_network_id(targetBridgeDeviceNetworkID) then
                    log.info("Adding bridge...")
                    driver:try_create_device({
                        type = "LAN",
                        label = "AutoBridge",
                        device_network_id = targetBridgeDeviceNetworkID,
                        profile = "Bridge"
                    })
                end
            end)
        end
    end,
    lifecycle_handlers = {
        added = function(driver, device, event, args, parent_device_id)
            -- not really much to do here
        end,
        init = function(driver, device)
            -- device.thread:call_on_schedule(100, function()
            -- end)
        end,
        infoChanged = function(driver, device)
            updateBridges()
        end
    },
    -- maybe this is all unnecessary due to register_channel_handler?
    capability_handlers = {
        [capabilities.refresh.ID] = {
            [capabilities.refresh.commands.refresh.NAME] = function(driver, device, args)
                processSetCommand(device, nil, nil) -- intentional
            end
        },
        [capabilities.switch.ID] = {
            [capabilities.switch.commands.on.NAME] = function(driver, device, args)
                processSetCommand(device, "switch", "on")
            end,
            [capabilities.switch.commands.off.NAME] = function(driver, device, args)
                processSetCommand(device, "switch", "off")
            end
        },
        [capabilities.doorControl.ID] = {
            [capabilities.doorControl.commands.open.NAME] = function(driver, device, args)
                processSetCommand(device, "door", "open")
            end,
            [capabilities.doorControl.commands.close.NAME] = function(driver, device, args)
                processSetCommand(device, "door", "closed")
            end
        },
        [capabilities.speechSynthesis.ID] = {
            [capabilities.speechSynthesis.commands.speak.NAME] = function(driver, device, command)
                processSetCommand(device, "phrase", command.args.phrase)
            end
        },
        [capabilities.alarm.ID] = {
            [capabilities.alarm.commands.siren.NAME] = function(driver, device, args)
                processSetCommand(device, "alarm", "siren")
            end,
            [capabilities.alarm.commands.off.NAME] = function(driver, device, args)
                processSetCommand(device, "alarm", "off")
            end
        }
    }
})

function driver.get_device_by_network_id(self, deviceNetworkID)
    for _, device in ipairs(self:get_devices()) do
        if device.device_network_id == deviceNetworkID then
            return device
        end
    end
end

function searchForBridges(bridgeFoundProc)
    log.info("Searching for bridges...")

    local ssdpSocket = socket.udp()
    ssdpSocket:setsockname("*", 0)
    ssdpSocket:settimeout(5) -- seconds, so if we go 5 seconds without receiving response, we'll end that loop

    local request = table.concat({'M-SEARCH * HTTP/1.1', --
    'MX: 1', -- seconds to delay response, whatever that means .. our Android app won't respect
    'MAN: "ssdp:discover"', --
    'HOST: 239.255.255.250:1900', --
    'ST: autobridge:SmartThings', --
    '' -- necessary to get last newline, otherwise won't get response
    }, '\r\n')

    ssdpSocket:sendto(request, "239.255.255.250", 1900)

    while true do
        local response, ip, _ = ssdpSocket:receivefrom() -- got this number from some python example .. size?

        if not response then
            break
        end

        local encodedTargetBridgeDeviceNetworkID, encodedBridgeName = string.match(response,
            'USN: autobridge:(..+):([^\r\n]+)')

        if encodedTargetBridgeDeviceNetworkID then
            local targetBridgeDeviceNetworkID = url.unescape(encodedTargetBridgeDeviceNetworkID)
            local bridgeName = url.unescape(encodedBridgeName)
            log.info("Found bridge " .. targetBridgeDeviceNetworkID .. " (" .. bridgeName .. ") at " .. ip)
            bridgeFoundProc(ip, targetBridgeDeviceNetworkID, bridgeName)
        end
    end

    ssdpSocket:close()
end

function processSetCommand(device, propertyName, propertyValue)
    if propertyName and propertyValue then
        log.info("Processing set property '" .. propertyName .. "' to '" .. propertyValue .. "'...")
    end

    local sourceID, deviceID = string.match(device.device_network_id, '(..+):(..+)')
    performHttpPost(device:get_parent_device(), {
        autoBridgeOperation = "syncDeviceState",
        sourceID = sourceID,
        deviceID = deviceID,
        propertyName = propertyName,
        propertyValue = propertyValue
    })
end

function performHttpPost(targetBridgeDevice, requestBody)
    local dateString = os.date("!%a, %d %b %Y %H:%M:%S GMT") -- "Mon, 09 Mar 2020 08:13:24 GMT"
    local requestBodyString = json.encode(requestBody)
    local validationKey = base64.decode(targetBridgeDevice.preferences.validationKey)
    local authorizationString = base64.encode(sha1.hmac_binary(validationKey, dateString .. requestBodyString))
    local url = "http://" .. targetBridgeDevice:get_field("authority") .. "/" .. targetBridgeDevice.device_network_id
    local responseBody = {}

    log.debug("Using date of: " .. dateString)
    log.debug("Calculated authorization string of: " .. authorizationString)

    log.info("Performing HTTP POST to: " .. url)

    local _, code = http.request({
        method = 'POST',
        url = url,
        sink = ltn12.sink.table(responseBody),
        headers = {
            ['Content-Type'] = "application/json",
            ['Authorization'] = authorizationString,
            ['Date'] = dateString,
            ['Content-Length'] = #requestBodyString
        },
        source = ltn12.source.string(requestBodyString)
    })

    log.debug("Received response code " .. code .. " with body: " .. json.encode(responseBody))

    return code, responseBody
end

function updateBridges()
    searchForBridges(function(ip, targetBridgeDeviceNetworkID, bridgeName)
        local targetBridgeDevice = driver:get_device_by_network_id(targetBridgeDeviceNetworkID)

        if targetBridgeDevice then
            log.info("Setting bridge host to " .. ip)
            targetBridgeDevice:set_field("authority", ip .. ":1035")
            performHttpPost(targetBridgeDevice, {
                autoBridgeOperation = "setHubAuthority",
                hubAuthority = driver.webServer.ip .. ":" .. driver.webServer.port
            })
        end
    end)
end

driver:call_with_delay(2, updateBridges)
driver:call_on_schedule(100, updateBridges)

webServer:post('/auto-bridge/:targetID', function(request, response)
    log.debug("Received request")

    -- TODO validate signature

    local bodyObject = json.decode(request:get_body())
    local targetBridgeDevice = driver:get_device_by_network_id(request.params.targetID)

    if targetBridgeDevice and bodyObject then
        log.info("Performing operation: " .. bodyObject.autoBridgeOperation)
        if bodyObject.autoBridgeOperation == "syncSources" then
            -- sourceIDs
            -- should look for any child device with wrong source
            -- and delete them?
        elseif bodyObject.autoBridgeOperation == "syncSourceDevices" then
            for _, deviceDefinition in ipairs(bodyObject.devices) do
                local deviceNetworkID = bodyObject.sourceID .. ":" .. deviceDefinition.deviceID
                if not driver:get_device_by_network_id(deviceNetworkID) then
                    log.info("Creating device: " .. deviceDefinition.name)
                    driver:try_create_device({
                        type = "LAN",
                        label = deviceDefinition.name,
                        device_network_id = deviceNetworkID,
                        profile = deviceDefinition.typeDisplayName,
                        parent_device_id = targetBridgeDevice.id
                    })
                end
            end
            -- should look for any devices with this source that aren't in this list
            -- and delete them?
        elseif bodyObject.autoBridgeOperation == "syncDeviceState" then
            log.info("Setting property '" .. bodyObject.propertyName .. "' to '" .. bodyObject.propertyValue .. "'...")

            local deviceNetworkID = bodyObject.sourceID .. ":" .. bodyObject.deviceID
            local device = driver:get_device_by_network_id(deviceNetworkID)

            local capabilityID = attributeIDToCapabilityIDTable[bodyObject.propertyName]
            if capabilityID then
                local cachedValue, _ = device:get_latest_state('main', capabilityID, bodyObject.propertyName)
                if cachedValue ~= bodyObject.propertyValue then
                    local capabilityObject = capabilities[capabilityID]
                    if capabilityObject then
                        local attributeObject = capabilityObject[bodyObject.propertyName]
                        if attributeObject then
                            for _, value in pairs(attributeObject) do
                                if value.NAME == bodyObject.propertyValue then
                                    device:emit_event(value())
                                end
                            end
                        end
                    end
                end
            end
        end
    end

    response:send("")
end)

webServer:listen()

cosock.spawn(function()
    while true do
        webServer:tick()
    end
end)

driver:run()
