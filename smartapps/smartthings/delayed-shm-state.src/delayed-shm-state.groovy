/**
 *  Copyright 2015 SmartThings
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Arm/disarm SHM with a button after x Seconds
 *
 *  Author: Luis Pinto
 */
definition(
    name: "Delayed SHM state",
    namespace: "smartthings",
    author: "Luis Pinto",
    description: "Change SHM with a switch button after x seconds",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Meta/intruder_motion-presence.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Meta/intruder_motion-presence@2x.png"
)

preferences {
	section("Control Switch") {
		input "theSwitch", "capability.switch",title:"Select a switch", required:false, multiple: true
		input "secondsLater", "number", title: "Arm how many seconds later?", required:true
	}
    section("Options for Arm") {
        input "messageOn","text",title:"Play this message when switch is activated", required:false, multiple: false
        input "messageOnURL","text",title:"Play this sound when switch is activated", required:false, multiple: false
        input "messageOnNow","text",title:"Play this message when active", required:false, multiple: false
		input "sonosOn", "capability.musicPlayer", title: "On this Speaker player", required: false
        input "volumeOn", "number", title: "Temporarily change volume", description: "0-100%", required: false
        input "sendPushOn", "bool", required: false, title: "Send Push Notification when switch is activated?"
        input "sendPushOnNow", "bool", required: false, title: "Send Push Notification when alarm is activated?"
        input "modeForArmed", "mode", title: "Change a mode", multiple: false, required:false
	}
    section("Options for disarm") {
        input "messageOff","text",title:"Play this message", required:false, multiple: false
        input "messageOffURL","text",title:"Play this sound when switch is activated", required:false, multiple: false
		input "sonosOff", "capability.musicPlayer", title: "On this Speaker player", required: false
        input "volumeOff", "number", title: "Temporarily change volume", description: "0-100%", required: false
        input "sendPushOff", "bool", required: false, title: "Send Push Notification also?"
        input "modeForDisarmed", "mode", title: "Change to mode", multiple: false, required:false
	}
}

def subscribeToEvents() {
    if(theSwitch)
    {
		subscribe(theSwitch, "switch.on", switchOnHandler, [filterEvents: false])
		subscribe(theSwitch, "switch.off", switchOffHandler, [filterEvents: false])
	}        
}

def installed() {
	log.debug "Installed with settings: ${settings}"
    subscribeToEvents();
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
    subscribeToEvents();
}

def switchOffHandler(evt) {
	log.debug "Switch ${theSwitch} turned: ${evt.value}"
    
	def status = location.currentState("alarmSystemStatus")?.value
    
	log.debug "SHM Status is ${status}"
	if (evt.value == "off"){
		if (messageOff) {
        	if (sendPushOff) {
        		sendPush(messageOff)
        	}
			log.debug "Playing message Off"
			state.sound = textToSpeech(messageOff instanceof List ? messageOff[0] : messageOff)
			sonosOff.playTrack(state.sound.uri, state.sound.duration, volumeOff)
		}
        if (messageOffURL) {
			state.sound = [uri: messageOffURL, duration: "10"]
			sonosOff.playTrack(state.sound.uri, state.sound.duration, volumeOff)        
		}
		sendLocationEvent(name: "alarmSystemStatus", value: "off")
        if (modeForDisarmed)
			location.setMode(modeForDisarmed)
	}
}

def switchOnHandler(evt) {
	log.debug "Switch ${theSwitch} turned: ${evt.value}"
	def delay = secondsLater
    
	def status = location.currentState("alarmSystemStatus")?.value
    
	log.debug "SHM status is ${status}"
	if (evt.value == "on") {
		if (messageOn) {
        	if (sendPushOn) {
        		sendPush(messageOn)
        	}
        	log.debug "Playing message On"
			state.sound = textToSpeech(messageOn instanceof List ? messageOn[0] : messageOn)
			sonosOn.playTrack(state.sound.uri, state.sound.duration, volumeOn)
		}
        if (messageOnURL) {
			state.sound = [uri: messageOnURL, duration: "10"]
			sonosOff.playTrack(state.sound.uri, state.sound.duration, volumeOn)        
		}

		runIn(delay, turnOnAlarm)
	}
}

def turnOnAlarm() {
    if (messageOnNow) {
        if (sendPushOnNow) {
            sendPush(messageOnNow)
        }
        log.debug "Playing message On Now"
        state.sound = textToSpeech(messageOnNow instanceof List ? messageOnNow[0] : messageOnNow)
        sonosOn.playTrack(state.sound.uri, state.sound.duration, volumeOn)
    }

    sendLocationEvent(name: "alarmSystemStatus", value: "stay")
    if (modeForArmed)
		location.setMode(modeForArmed)
}