/**
 *
 *	Copyright 2019 SmartThings
 *
 *	Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *	in compliance with the License. You may obtain a copy of the License at:
 *
 *		http://www.apache.org/licenses/LICENSE-2.0
 *
 *	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *	on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *	for the specific language governing permissions and limitations under the License.
 *
 * Based on original smartthings blinds dth
 *
 */
import groovy.json.JsonOutput
import physicalgraph.zigbee.zcl.DataType

metadata {
    definition(name: "Ikea Window Shade", namespace: "smartthings", author: "Luis Pinto", ocfDeviceType: "oic.d.blind", mnmn: "SmartThings", vid: "generic-shade-3") {
        capability "Actuator"
        capability "Configuration"
        capability "Refresh"
        capability "Window Shade"
		capability "Window Shade Preset"
        capability "Health Check"
        capability "Switch Level"
        capability "Switch"
		capability "Battery"
        
        command "pause"

		fingerprint manufacturer: "IKEA of Sweden", model: "KADRILJ roller blind", deviceJoinName: "IKEA Window Treatment" // raw description 01 0104 0202 00 09 0000 0001 0003 0004 0005 0020 0102 1000 FC7C 02 0019 1000 //IKEA KADRILJ Blinds
		fingerprint manufacturer: "IKEA of Sweden", model: "FYRTUR block-out roller blind", deviceJoinName: "IKEA Window Treatment" // raw description 01 0104 0202 01 09 0000 0001 0003 0004 0005 0020 0102 1000 FC7C 02 0019 1000 //IKEA FYRTUR Blinds

//		fingerprint manufacturer: "IKEA of Sweden", inClusters: "0000,0001,0003,0004", model: "FYRTUR block-out roller blind"
//		fingerprint manufacturer: "IKEA of Sweden", model: "KADRILJ roller blind", deviceJoinName: "IKEA KADRILJ Blinds" // raw description 01 0104 0202 00 09 0000 0001 0003 0004 0005 0020 0102 1000 FC7C 02 0019 1000
    }
    
    preferences {
		input "preset", "number", title: "Preset position", description: "Set the window shade preset position", defaultValue: 50, range: "1..100", required: false, displayDuringSetup: false
	}

    tiles(scale: 2) {
        multiAttributeTile(name:"windowShade", type: "generic", width: 6, height: 4) {
            tileAttribute("device.windowShade", key: "PRIMARY_CONTROL") {
 				attributeState "open", label: 'Open', action: "close", icon: "st.shades.shade-open", backgroundColor: "#00A0DC", nextState: "closing"
				attributeState "closed", label: 'Closed', action: "open", icon: "st.shades.shade-closed", backgroundColor: "#ffffff", nextState: "opening"
				attributeState "partially open", label: 'Partially open', action: "close", icon: "st.shades.shade-open", backgroundColor: "#00A0DC", nextState: "closing"
				attributeState "opening", label: 'Opening', action: "pause", icon: "st.shades.shade-opening", backgroundColor: "#00A0DC", nextState: "partially open"
				attributeState "closing", label: 'Closing', action: "pause", icon: "st.shades.shade-closing", backgroundColor: "#ffffff", nextState: "partially open"
            }
            tileAttribute ("device.windowShadeLevel", key: "SLIDER_CONTROL") {
				attributeState "shadeLevel", action:"setLevel"
			}
        }
        standardTile("contPause", "device.switch", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "pause", label:"", icon:'st.sonos.pause-btn', action:'pause', backgroundColor:"#cccccc"
		}
        standardTile("presetPosition", "device.presetPosition", width: 2, height: 2, decoration: "flat") {
			state ("default", label: "Preset", action:"presetPosition", icon:"st.Home.home2")
		}
		standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 1) {
			state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
		}
        valueTile("shadeLevel", "device.level", width: 4, height: 1) {
            state "level", label: 'Shade is ${currentValue}% down', defaultState: true
        }
       	valueTile("batteryLevel", "device.battery", width: 4, height: 1) {
			state "battery", label:"${currentValue}% battery", unit:"%"
		}
/*        controlTile("levelSliderControl", "device.level", "slider", width:2, height: 1, inactiveLabel: false) {
            state "level", action:"switch level.setLevel"
        }
*/
        main (["windowShade"])
		details(["windowShade", "contPause", "presetPosition", "refresh", "batteryLevel"])
//        details(["windowShade", "batteryLevel", "contPause", "presetPosition", "shadeLevel", "levelSliderControl", "refresh"])
	}
}

private getCLUSTER_BATTERY_LEVEL() { 0x0001 }
private getCLUSTER_WINDOW_COVERING() { 0x0102 }
private getCOMMAND_OPEN() { 0x00 }
private getCOMMAND_CLOSE() { 0x01 }
private getCOMMAND_PAUSE() { 0x02 }
private getCOMMAND_GOTO_LIFT_PERCENTAGE() { 0x05 }
private getATTRIBUTE_POSITION_LIFT() { 0x0008 }
private getATTRIBUTE_CURRENT_LEVEL() { 0x0000 }
private getCOMMAND_MOVE_LEVEL_ONOFF() { 0x04 }
private getBATTERY_PERCENTAGE_REMAINING() { 0x0021 }

private List<Map> collectAttributes(Map descMap) {
	List<Map> descMaps = new ArrayList<Map>()

	descMaps.add(descMap)

	if (descMap.additionalAttrs) {
		descMaps.addAll(descMap.additionalAttrs)
	}

	return descMaps
}

// Parse incoming device messages to generate events
def parse(String description) {
    log.trace "description:- ${description}"
    if (description?.startsWith("read attr -")) {
        Map descMap = zigbee.parseDescriptionAsMap(description)
        if (isBindingTableMessage(description)) {
		    log.trace "BindingTableMessage"
			parseBindingTableMessage(description)
		} else if (supportsLiftPercentage() && descMap?.clusterInt == CLUSTER_WINDOW_COVERING && descMap.value) {
            log.trace "attr: ${descMap?.attrInt}, value: ${descMap?.value}, descValue: ${Integer.parseInt(descMap.value, 16)}, ${device.getDataValue("model")}"
            List<Map> descMaps = collectAttributes(descMap)
            def liftmap = descMaps.find { it.attrInt == ATTRIBUTE_POSITION_LIFT }
            if (liftmap && liftmap.value) {
                def newLevel = zigbee.convertHexToInt(liftmap.value)
                levelEventHandler(newLevel)
            }
        } else if (!supportsLiftPercentage() && descMap?.clusterInt == zigbee.LEVEL_CONTROL_CLUSTER && descMap.value) {
		    log.trace "Doesn't support lift message"
            def valueInt = Math.round((zigbee.convertHexToInt(descMap.value)) / 255 * 100)

            levelEventHandler(valueInt)
        } else if (reportsBatteryPercentage() && descMap?.clusterInt == zigbee.POWER_CONFIGURATION_CLUSTER && zigbee.convertHexToInt(descMap?.attrId) == BATTERY_PERCENTAGE_REMAINING && descMap.value) {
		    log.trace "Just to report battery"
			def batteryLevel = zigbee.convertHexToInt(descMap.value)
			batteryPercentageEventHandler(batteryLevel)
																				  
		} 
/*        if (descMap?.clusterInt == CLUSTER_BATTERY_LEVEL && descMap.value) {
            log.debug "attr: ${descMap?.attrInt}, value: ${descMap?.value}, descValue: ${Integer.parseInt(descMap.value, 16)}"
            sendEvent(name: "battery", value: Integer.parseInt(descMap.value, 16))
        } */
    } else if (description.startsWith("catchall: 0104 0102 01 01 0000 00 ") && description.endsWith (" 00 00 0000 0B 01 0100")) {
        log.debug "closed"
        sendEvent(name: "windowShade", value: "closed")
    } else if (description.startsWith("catchall: 0104 0102 01 01 0000 00 ") && description.endsWith (" 00 00 0000 0B 01 0500")) {
        log.debug "partially open"
        sendEvent(name: "windowShade", value: "partially open")
    } else if (description.startsWith("catchall: 0104 0102 01 01 0000 00 ") && description.endsWith (" 00 00 0000 0B 01 0000")) {
        log.debug "open"
        sendEvent(name: "windowShade", value: "open")
    } else {
        log.debug "Dont know what to do with this"
    }
}

def levelEventHandler(currentLevel) {
    def lastLevel = device.currentValue("level")
    log.trace "levelEventHandle - currentLevel: ${currentLevel} lastLevel: ${lastLevel}"
    if (lastLevel == "undefined" || currentLevel == lastLevel) { //Ignore invalid reports
        log.trace "Ignore invalid reports"
    } else {
        sendEvent(name: "level", value: currentLevel)
        if (currentLevel == 0 || currentLevel == 100) {
            sendEvent(name: "windowShade", value: currentLevel == 0 ? "open" : "closed")
        } else {
            if (lastLevel > currentLevel) {
                sendEvent([name:"windowShade", value: "opening"])
            } else if (lastLevel < currentLevel) {
                sendEvent([name:"windowShade", value: "closing"])
            }
            runIn(1, "updateFinalState", [overwrite:true])
        }
    }
}

def batteryPercentageEventHandler(batteryLevel) {
	if (batteryLevel != null) {
		batteryLevel = Math.min(100, Math.max(0, batteryLevel))
		sendEvent([name: "battery", value: batteryLevel, unit: "%", descriptionText: "{{ device.displayName }} battery was {{ value }}%"])
	}
}

def updateFinalState() {
    def level = device.currentValue("level")
    log.trace "updateFinalState: ${level}"
    if (level > 0 && level < 100) {
        sendEvent(name: "windowShade", value: "partially open")
    }
}

def close() {
    log.info "close()"
    zigbee.command(CLUSTER_WINDOW_COVERING, COMMAND_CLOSE)
}

def open() {
    log.info "open()"
    zigbee.command(CLUSTER_WINDOW_COVERING, COMMAND_OPEN)
}

def off() {
    log.info "close()"
    zigbee.command(CLUSTER_WINDOW_COVERING, COMMAND_CLOSE)
}

def on() {
    log.info "open()"
    zigbee.command(CLUSTER_WINDOW_COVERING, COMMAND_OPEN)
}

def presetPosition() {
    log.info "preseting Position"
    setLevel(preset ?: 50)
}

def setLevel(data, rate = null) {
    log.info "setLevel($data)"
    def cmd
    def valueInt = data.toInteger() 

	sendEvent(name: "level", value: valueInt)

    if (supportsLiftPercentage()) {
//        cmd = zigbee.command(CLUSTER_WINDOW_COVERING, COMMAND_GOTO_LIFT_PERCENTAGE, zigbee.convertToHexString(0 , 2))
		if (valueInt == 100) {
		    log.info "setLevel -> Close"
		    cmd = zigbee.command(CLUSTER_WINDOW_COVERING, COMMAND_CLOSE)
        }
        else if (valueInt == 0) {
		    log.info "setLevel -> Open"
		    cmd = zigbee.command(CLUSTER_WINDOW_COVERING, COMMAND_OPEN)
        }
        else
	        cmd = zigbee.command(CLUSTER_WINDOW_COVERING, COMMAND_GOTO_LIFT_PERCENTAGE, zigbee.convertToHexString(valueInt , 2))
    } else {
        cmd = zigbee.command(zigbee.LEVEL_CONTROL_CLUSTER, COMMAND_MOVE_LEVEL_ONOFF, zigbee.convertToHexString(Math.round(valueInt * 255 / 100), 2))
    }

    return cmd
}


def pause() {
    log.info "pause()"
//    def level = device.currentValue("level")
    // If the window shade isn't moving when we receive a pause() command then just echo back the current state for the mobile client.
	if (device.currentValue("windowShade") != "opening" && device.currentValue("windowShade") != "closing") {
		sendEvent(name: "windowShade", value: device.currentValue("windowShade"), isStateChange: true, displayed: false)
	}
    zigbee.command(CLUSTER_WINDOW_COVERING, COMMAND_PAUSE)
}

/**
 * PING is used by Device-Watch in attempt to reach the Device
 * */
def ping() {
    return refresh()
}

def refresh() {
    log.info "refresh()"
    def cmds
    if (supportsLiftPercentage()) {
        cmds = zigbee.readAttribute(CLUSTER_WINDOW_COVERING, ATTRIBUTE_POSITION_LIFT) + zigbee.readAttribute(CLUSTER_BATTERY_LEVEL, 0x0021) 
    } else {
        cmds = zigbee.readAttribute(zigbee.LEVEL_CONTROL_CLUSTER, ATTRIBUTE_CURRENT_LEVEL) + zigbee.readAttribute(CLUSTER_BATTERY_LEVEL, 0x0021) 
    }
    return cmds
}

def installed() {
    sendEvent(name: "supportedWindowShadeCommands", value: JsonOutput.toJson(["open", "close", "pause"]), displayed: true)
    sendEvent(name: "battery", value: 100)
    refresh()
}

def configure() {
    // Device-Watch allows 2 check-in misses from device + ping (plus 2 min lag time)
    log.info "configure()"
    sendEvent(name: "checkInterval", value: 2 * 60 * 60 + 2 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
    log.debug "Configuring Reporting and Bindings."

    def cmds
    if (supportsLiftPercentage()) {
		cmds = zigbee.configureReporting(CLUSTER_WINDOW_COVERING, ATTRIBUTE_POSITION_LIFT, DataType.UINT8, 2, 600, null)
//        cmds = zigbee.configureReporting(CLUSTER_WINDOW_COVERING, ATTRIBUTE_POSITION_LIFT, DataType.UINT8, 0, 600, null)
    } else {
        cmds = zigbee.levelConfig()
    }
    
    if (usesLocalGroupBinding()) {
		cmds += readDeviceBindingTable()
	}

	if (reportsBatteryPercentage()) {
		cmds += zigbee.configureReporting(zigbee.POWER_CONFIGURATION_CLUSTER, BATTERY_PERCENTAGE_REMAINING, DataType.UINT8, 30, 21600, 0x01)
	}
    return refresh() + cmds
}

def usesLocalGroupBinding() {
	isIkeaKadrilj() || isIkeaFyrtur()
}

private def parseBindingTableMessage(description) {
	Integer groupAddr = getGroupAddrFromBindingTable(description)
	if (groupAddr) {
		List cmds = addHubToGroup(groupAddr)
		cmds?.collect { new physicalgraph.device.HubAction(it) }
	}
}

private Integer getGroupAddrFromBindingTable(description) {
	log.info "Parsing binding table - '$description'"
	def btr = zigbee.parseBindingTableResponse(description)
	def groupEntry = btr?.table_entries?.find { it.dstAddrMode == 1 }
	log.info "Found ${groupEntry}"
	!groupEntry?.dstAddr ?: Integer.parseInt(groupEntry.dstAddr, 16)
}

private List addHubToGroup(Integer groupAddr) {
	["st cmd 0x0000 0x01 ${CLUSTER_GROUPS} 0x00 {${zigbee.swapEndianHex(zigbee.convertToHexString(groupAddr,4))} 00}", "delay 200"]
}

private List readDeviceBindingTable() {
	["zdo mgmt-bind 0x${device.deviceNetworkId} 0", "delay 200"]
}

def supportsLiftPercentage() {
	isIkeaKadrilj() || isIkeaFyrtur()
}

def shouldInvertLiftPercentage() {
	return isIkeaKadrilj() || isIkeaFyrtur()
}

def reportsBatteryPercentage() {
	return isIkeaKadrilj() || isIkeaFyrtur()
}

def isIkeaKadrilj() {
	device.getDataValue("model") == "KADRILJ roller blind"
}

def isIkeaFyrtur() {
	device.getDataValue("model") == "FYRTUR block-out roller blind"
}