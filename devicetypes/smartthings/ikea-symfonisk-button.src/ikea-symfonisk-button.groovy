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
    definition(name: "Ikea Symfonisk Button", namespace: "smartthings", author: "Luis Pinto", ocfDeviceType: "x.com.st.d.remotecontroller", mnmn: "SmartThings") {
		capability "Actuator"
		capability "Switch"
		capability "Button"
		capability "Switch Level"
		capability "Configuration"
		capability "Health Check"
		capability "Battery"
		capability "Sensor"

		fingerprint inClusters: "0000, 0001, 0003, 0020, 1000", outClusters: "0003, 0004, 0006, 0008, 0019, 1000", manufacturer: "IKEA of Sweden", model: "SYMFONISK Sound Controller", deviceJoinName: "IKEA TRÅDFRI Symfonisk"
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
    
    preferences {
        input "step", "number", title: "Move Step", description: "Adjust steps while moving rotary", default: 10,
              range: "*..*", displayDuringSetup: false
        input "debounceTime", "number", title: "Debounce timer", description: "debounce events with millisecons", default: 5,
              range: "*..*", displayDuringSetup: false
    }
}

private getCLUSTER_BATTERY_LEVEL() { 0x0001 }
private getBATTERY_VOLTAGE_ATTR() { 0x0021 }

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
    def event = zigbee.getEvent(description)
    state.descriptionPrev = state.description

    def descMap = zigbee.parseDescriptionAsMap(description)
    if (descMap.clusterInt == zigbee.POWER_CONFIGURATION_CLUSTER && descMap.attrInt == BATTERY_VOLTAGE_ATTR) {
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
            } else if (descMap.data[0][1] == "1") {
                log.debug "button 3 click"
                  sendButtonEvent(3, "pushed")
            }
        } else if (descMap.commandInt == 0x01) {
            state.direction = descMap.data[0][1]
            state.moveStart = now()
            log.debug "dimmer start moving"
            dim(true)
        } else if (descMap.commandInt == 0x03) {
            dim(false)
        }
    } else if (isBindingTableMessage(description)) {
            def result = []
            Integer groupAddr = getGroupAddrFromBindingTable(description)
            if (groupAddr != null) {
                List cmds = addHubToGroup(groupAddr)
                result = cmds?.collect { new physicalgraph.device.HubAction(it) }
            } else {
                groupAddr = 0x0000
                List cmds = addHubToGroup(groupAddr) +
                        zigbee.command(CLUSTER_GROUPS, 0x00, "${zigbee.swapEndianHex(zigbee.convertToHexString(groupAddr, 4))} 00")
                result = cmds?.collect { new physicalgraph.device.HubAction(it) }
            }
            return result
    } else {
        log.warn "DID NOT PARSE MESSAGE for description : $description"
        log.debug "${descMap}"
    }
    
}

def dim(repeat=false) {
    def moveStep = 10

    if (step)
        moveStep = step
    def multiplier = 10
    def moveStopped = now()
    def elapsed = moveStopped - state.moveStart
    def lastValue = device.currentValue("level")==null?0:device.currentValue("level")
    def dimmerValue = 0

    if(state.direction == "1") {
        dimmerValue = Math.round(lastValue - (elapsed/50*(moveStep/multiplier)))
    } else {
        dimmerValue = Math.round(lastValue + (elapsed/50*(moveStep/multiplier)))
    }

    if (dimmerValue > 100)
        dimmerValue = 100               

    if (dimmerValue < 0)
        dimmerValue = 0
    
    log.debug "dimmer stopped moving, new dimmer value=$dimmerValue"
    setLevel(dimmerValue)
    
    if (dimmerValue != 100 && dimmerValue != 0 && repeat) {
      def runTime = new Date(now() + debounceTime)
      runOnce(runTime, reParse, [overwrite: true])
    }
}

public def reParse(){
    parse(state.descriptionPrev)
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

def ping() {
    log.info "ping()"
}


def installed() {
    // These devices don't report regularly so they should only go OFFLINE when Hub is OFFLINE
    sendEvent(name: "DeviceWatch-Enroll", value: JsonOutput.toJson([protocol: "zigbee", scheme:"untracked"]), displayed: false)
}

def configure() {
    // Device-Watch allows 2 check-in misses from device + ping (plus 2 min lag time)
    log.info "configure()"
    sendEvent(name: "checkInterval", value: 2 * 60 * 60 + 2 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
    log.debug "Configuring Reporting and Bindings."

    def cmds
    cmds = zigbee.configureReporting(zigbee.POWER_CONFIGURATION_CLUSTER, BATTERY_VOLTAGE_ATTR, DataType.UINT8, 30, 21600, 0x01) +
            zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, BATTERY_VOLTAGE_ATTR) +
            zigbee.addBinding(zigbee.ONOFF_CLUSTER) +
            readDeviceBindingTable() // Need to read the binding table to see what group it's using
            
    cmds
}

private List addHubToGroup(Integer groupAddr) {
    ["st cmd 0x0000 0x01 ${CLUSTER_GROUPS} 0x00 {${zigbee.swapEndianHex(zigbee.convertToHexString(groupAddr,4))} 00}",
     "delay 200"]
}

private List readDeviceBindingTable() {
    ["zdo mgmt-bind 0x${device.deviceNetworkId} 0",
     "delay 200"]
}
