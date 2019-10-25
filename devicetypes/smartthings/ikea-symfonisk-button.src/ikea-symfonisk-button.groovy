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
import physicalgraph.zigbee.zcl.DataType

metadata {
    definition(name: "Ikea Symfonisk Button", namespace: "smartthings", author: "Luis Pinto", ocfDeviceType: "x.com.st.d.remotecontroller", mnmn: "SmartThings") {
		capability "Actuator"
		capability "Switch"
		capability "Button"
		capability "Switch Level"
		capability "Configuration"
		capability "Health Check"
		capability "Battery"
        capability "Refresh"

		fingerprint inClusters: "0000,0001,0003,0004", manufacturer: "IKEA of Sweden", model: "SYMFONISK Sound Controller"
    }


	tiles(scale: 2) {
		multiAttributeTile(name:"switch", type: "generic", width: 6, height: 4, canChangeIcon: true){
			tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
				attributeState "on", label:'${name}', action:"switch.off", backgroundColor:"#00A0DC", nextState:"turningOff"
				attributeState "off", label:'${name}', action:"switch.on", backgroundColor:"#ffffff", nextState:"turningOn"
				attributeState "turningOn", label:'${name}', action:"switch.off", backgroundColor:"#00A0DC", nextState:"turningOff"
				attributeState "turningOff", label:'${name}', action:"switch.on", backgroundColor:"#ffffff", nextState:"turningOn"
			}
			tileAttribute ("device.level", key: "SLIDER_CONTROL") {
				attributeState "level", action:"switch level.setLevel", defaultState: true
			}
		}
        valueTile("battery", "device.battery", decoration: "flat", inactiveLabel: false, width: 1, height: 1) {
			state "battery", label:'${currentValue}% battery', unit:""
		}

		main "switch"
		details(["switch", "battery"])
	}
    
}

private getCLUSTER_BATTERY_LEVEL() { 0x0001 }

private sendButtonEvent(buttonNumber, buttonState) {
	def child = childDevices?.find { channelNumber(it.deviceNetworkId) == buttonNumber }

	if (child) {
		def descriptionText = "$child.displayName was $buttonState" // TODO: Verify if this is needed, and if capability template already has it handled
		child?.sendEvent([name: "button", value: buttonState, data: [buttonNumber: 1], descriptionText: descriptionText, isStateChange: true])
        log.debug "$child button click"

	} else {
		log.debug "Child device $buttonNumber not found!"
	}
}

// Parse incoming device messages to generate events
def parse(String description) {
	log.debug "description is $description"
    
    def descMap = zigbee.parseDescriptionAsMap(description)
    log.debug descMap
    if (descMap.clusterInt == zigbee.POWER_CONFIGURATION_CLUSTER && descMap.attrInt == 0x0021) {
        sendEvent(name: "battery", value: zigbee.convertHexToInt(descMap.value))
    } else if (descMap && descMap.clusterInt == 0x0006) {
        if (descMap.commandInt == 0x02) {
        	log.debug "button 1 click"
            sendButtonEvent(1, "pushed")
            sendEvent(name: "switch", value: device.currentValue("switch") == "on" ? "off" : "on")
        }
    } else if (descMap && descMap.clusterInt == 0x0008) {
        def currentLevel = device.currentValue("level") as Integer ?: 0
        if (descMap.commandInt == 0x02) {
        	if (descMap.data[0][1] == "0")
            {
                log.debug "button 2 click"
  		        sendButtonEvent(2, "pushed")
//                sendEvent(name: "button", value: "pushed", data: [buttonNumber: 2], isStateChange: true)
            } else if (descMap.data[0][1] == "1") {
                log.debug "button 3 click"
  		        sendButtonEvent(3, "pushed")
//                sendEvent(name: "button", value: "pushed", data: [buttonNumber: 3], isStateChange: true)
            }
		} else if (descMap.commandInt == 0x01) {
            state.direction = descMap.data[0][1]
            state.moveStart = now()
        } else if (descMap.commandInt == 0x03) {
            def moveStopped = now()
            def elapsed = moveStopped - state.moveStart
            def lastValue = device.currentValue("level")==null?0:device.currentValue("level")
            def dimmerValue = 0
            log.debug "stop move"
            if(state.direction == "1")
            dimmerValue = lastValue - elapsed/50
            else
                dimmerValue = lastValue + elapsed/50

            if (dimmerValue > 100)
            dimmerValue = 100           	

            if (dimmerValue < 0)
            dimmerValue = 0

            sendEvent(name: "level", value: dimmerValue)
        }
    } else {
        log.warn "DID NOT PARSE MESSAGE for description : $description"
        log.debug "${descMap}"
    }
	
}

private getREMOTE_BUTTONS() {
	[PLAY:1,
	 NEXT:2,
	 PREVIOUS:3]
}

private getButtonLabel(buttonNum) {
	def label = "Button ${buttonNum}"
	return label
}

private getButtonName(buttonNum) {
	return "${device.displayName} " + getButtonLabel(buttonNum)
}

private void createChildButtonDevices() {
	
    def numberOfButtons = 3
    state.oldLabel = device.label

	log.debug "Creating $numberOfButtons children"

	for (i in 1..numberOfButtons) {
		log.debug "Creating child $i"
		def supportedButtons = ["pushed"]
		def child = addChildDevice("Child Button", "${device.deviceNetworkId}:${i}", device.hubId,
				[completedSetup: true, label: getButtonName(i),
				 isComponent: true, componentName: "button$i", componentLabel: getButtonLabel(i)])

		child.sendEvent(name: "supportedButtonValues", value: supportedButtons.encodeAsJSON(), displayed: false)
		child.sendEvent(name: "numberOfButtons", value: 1, displayed: false)
		child.sendEvent(name: "button", value: "pushed", data: [buttonNumber: 1], displayed: false)
	}
}

def manageButtons() {
    createChildButtonDevices()
}

private channelNumber(String dni) {
	dni.split(":")[-1] as Integer
}

def updated() {
    // initialize counter
    log.info "updated()"
    state.moveStart = now()
    
    if (childDevices && device.label != state.oldLabel) {
		childDevices.each {
			def newLabel = getButtonName(channelNumber(it.deviceNetworkId))
			it.setLabel(newLabel)
		}
		state.oldLabel = device.label
	}
    if (!childDevices)
    {
		manageButtons()
    }
}

def initialize() {
    // initialize counter
    log.info "initialize()"
    state.moveStart = now()
}

def close() {
    sendEvent(name: "switch", value: "off")
    log.info "close()"
}

def open() {
    sendEvent(name: "switch", value: "on")
    log.info "open()"
}

def off() {
    sendEvent(name: "switch", value: "off")
    log.info "close()"
}

def on() {
    sendEvent(name: "switch", value: "on")
    log.info "open()"
}

def setLevel(data) {
    log.info "setLevel($data)"
    sendEvent(name: "level", value: data)
}

def refresh() {
    log.info "refresh()"
}

def configure() {
    // Device-Watch allows 2 check-in misses from device + ping (plus 2 min lag time)
    log.info "configure()"
    sendEvent(name: "checkInterval", value: 2 * 60 * 60 + 2 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
    log.debug "Configuring Reporting and Bindings."

    def cmds

    cmds = zigbee.levelConfig()
    return refresh() + cmds
}