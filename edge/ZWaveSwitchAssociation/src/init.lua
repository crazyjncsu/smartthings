-- Z-Wave switch and dimmer with the stock behavior, plus a setting for association group 2.
--
-- GE/Jasco switches send a Basic Set to the nodes in group 2 when the paddle is pushed. Put two
-- switches in the group 2 of each other to make a virtual 3-way: either paddle controls both,
-- directly over Z-Wave, without the hub.
--
-- This driver has only generic fingerprints, so new devices with a manufacturer fingerprint in another
-- driver do not go to it. Move a device to it with:
--   smartthings edge:drivers:switch <deviceId> --hub=<hubId> --driver=<driverId>

local capabilities = require "st.capabilities"
local ZwaveDriver = require "st.zwave.driver"
local defaults = require "st.zwave.defaults"
local cc = require "st.zwave.CommandClass"
local Association = (require "st.zwave.CommandClass.Association")({ version = 2 })

local GROUP = 2

-- "0A, 0b 0C" -> { 10, 11, 12 }
local function parseNodeIDs(text)
    local nodeIDs = {}
    for token in string.gmatch(text or "", "[^,%s]+") do
        local nodeID = tonumber(token, 16)
        if nodeID and nodeID >= 1 and nodeID <= 232 then
            table.insert(nodeIDs, nodeID)
        end
    end
    return nodeIDs
end

local function formatNodeIDs(nodeIDs)
    local tokens = {}
    for _, nodeID in ipairs(nodeIDs or {}) do
        table.insert(tokens, string.format("%02X", nodeID))
    end
    return table.concat(tokens, ", ")
end

local function setProfile(device)
    local profile = device:is_cc_supported(cc.SWITCH_MULTILEVEL) and "switch-level-association" or "switch-binary-association"
    device:try_update_metadata({ profile = profile })
end

local function syncAssociations(device, text)
    local nodeIDs = parseNodeIDs(text)
    device.log.info_with({ hub_logs = true }, "Setting group " .. GROUP .. " to: " .. formatNodeIDs(nodeIDs))

    -- an empty node list removes all nodes from the group
    device:send(Association:Remove({ grouping_identifier = GROUP, node_ids = {} }))
    if #nodeIDs > 0 then
        device:send(Association:Set({ grouping_identifier = GROUP, node_ids = nodeIDs }))
    end
    device:send(Association:Get({ grouping_identifier = GROUP }))
end

-- logs the report and shows it in execute.data, so that the API can read it
local function associationReport(driver, device, command)
    local nodeIDs = formatNodeIDs(command.args.node_ids)
    device.log.info_with({ hub_logs = true }, string.format("Group %d has: %s (max %d)",
        command.args.grouping_identifier, nodeIDs, command.args.max_nodes_supported))
    device:emit_event(capabilities.execute.data({
        group = command.args.grouping_identifier,
        nodeIds = nodeIDs,
        maxNodes = command.args.max_nodes_supported
    }, { visibility = { displayed = false } }))
end

-- the API cannot change device settings, but it can send commands:
--   execute("setGroup2", { nodeIds = "0C" })  or  execute("getGroup2")
local function executeCommand(driver, device, command)
    if command.args.command == "setGroup2" then
        syncAssociations(device, (command.args.args or {}).nodeIds)
    elseif command.args.command == "getGroup2" then
        device:send(Association:Get({ grouping_identifier = GROUP }))
    elseif command.args.command == "getGroup" then
        device:send(Association:Get({ grouping_identifier = tonumber((command.args.args or {}).group) or 1 }))
    elseif command.args.command == "getGroupings" then
        device:send(Association:GroupingsGet({}))
    end
end

local driverTemplate = {
    supported_capabilities = {
        capabilities.switch,
        capabilities.switchLevel,
        capabilities.refresh
    },
    capability_handlers = {
        [capabilities.execute.ID] = {
            [capabilities.execute.commands.execute.NAME] = executeCommand
        }
    },
    lifecycle_handlers = {
        driverSwitched = function(driver, device)
            setProfile(device)
            device:send(Association:Get({ grouping_identifier = GROUP }))
        end,
        infoChanged = function(driver, device, event, args)
            local oldValue = args.old_st_store.preferences.group2NodeIds
            if device.preferences.group2NodeIds ~= oldValue then
                syncAssociations(device, device.preferences.group2NodeIds)
            end
        end
    },
    zwave_handlers = {
        [cc.ASSOCIATION] = {
            [Association.REPORT] = associationReport,
            [Association.GROUPINGS_REPORT] = function(driver, device, command)
                device.log.info_with({ hub_logs = true }, "Supported groupings: " .. command.args.supported_groupings)
            end
        }
    }
}

defaults.register_for_default_handlers(driverTemplate, driverTemplate.supported_capabilities)

local driver = ZwaveDriver("zwave-switch-association", driverTemplate)
driver:run()
