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
 *  Turn It On After 30 seconds
 *  Turn on a contact when a contact sensor opens after 30 seconds.
 *
 */
definition(
    name: "Delayed contact",
    namespace: "smartthings",
    author: "Luis Pinto",
    description: "When a real contact is turned on turn a virtual contact on also after x seconds.",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Meta/intruder_motion-presence.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Meta/intruder_motion-presence@2x.png"
)

preferences {
	section("When it opens..."){
		input "contacts", "capability.contactSensor", multiple: true, required: true
        input(name: "contactState", type: "enum", title: "State", options: ["Open","Closed"])
	}   
	section("Turn on a contact after x seconds..."){
		input "contact2", "capability.contactSensor", multiple: true, required: true
        input "secondsLater", "number", title: "How many seconds?"
	}
    section("Optionaly play a message if SHM armed") {
        input "message","text",title:"Play this message", required:false, multiple: false
		input "sonos", "capability.musicPlayer", title: "On this Speaker player", required: true
        input "volume", "number", title: "Temporarily change volume", description: "0-100%", required: false
	}    
}

def subscribeToEvents()
{
    if (contactState == "Open"){
		subscribe(contacts, "contact.open", contactHandler)
    }
    else{
		subscribe(contacts, "contact.closed", contactHandler)
    }
}

def installed() {
	log.debug "Installed with settings: ${settings}"
	subscribeToEvents();
}

def updated(settings) {
	log.debug "Updated with settings: ${settings}"
	unsubscribe()
	subscribeToEvents();
}

def contactHandler(evt) {
	def delay = secondsLater
	def status = location.currentState("alarmSystemStatus")?.value

    if (message && status != "off") {
		log.debug "Playing message"
        state.sound = textToSpeech(message instanceof List ? message[0] : message)
        sonos.playTrack(state.sound.uri, state.sound.duration, volume)
    }

	runIn(delay, turnOffSwitch)
}

def turnOffSwitch() {
	contact2.open()
	contact2.close()
}