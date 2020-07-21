/**
    This is the child driver for the composite GoGoGate 2 driver for Hubitat
    The most recent version is available at:
        https://raw.githubusercontent.com/mgroeninger/Hubitat-gogogate2/master/GoGoGate_child_driver.groovy
    
    For more documentation please see https://github.com/mgroeninger/Hubitat-gogogate2
**/
import groovy.time.*

metadata {
    definition (name: "GoGoGate 2 Door Child", namespace: "gogogate2-composite", author: "Matt Groeninger") {
        capability "DoorControl"
        capability "GarageDoorControl"
		capability "Sensor"
		capability "Switch"
        capability "ContactSensor"

		attribute "lastUpdated", "string"
		attribute "battery", "string"
		
		command "open"
        command "close"
		command "on"
		command "off"
    }
}

preferences {
    input "transitionInterval", "number", title: "Door Transition Interval", description: "Duration to ignore door state while opening/closing (in seconds)", range: "2..60", defaultValue: 45
}

def initialize() {
	sendUpdateEvent()
}

def on() {
    cmdToggle("open")   
}

def off() {
    cmdToggle("close")
}

def open() {
    cmdToggle("open")   
}

def close() {
    cmdToggle("close")
}

public getVerbs(String key) {
	def verbs = [
		"open": ["open", "opening", "open","on"], 
		"opened": ["opened", "opening", "open","on"], 
		"close":["close", "closing", "closed","off"],
		"closed":["closed", "closing", "closed","off"]
	]
	verbs.get(key) ?: false
}

public cmdToggle(String action) {
    def id = device.deviceNetworkId[-1] as Integer
    def verbs = getVerbs(action)
	if (!verbs) {
		parent.log("Invalid action ${action} submitted to door.","error")
		return
	}
	if (!verbs.contains(device.currentValue("door"))) {
		parent.log("Telling child door ${id} (${device.getLabel()}) to ${verbs.get(0)}","debug")
		parent.toggleDoor(id)
		parent.log("Setting ${id} (${device.getLabel()}) to ${verbs.get(1)}","debug")
        sendEvent(name: "switch", value: verbs.get(3), isStateChange: true)
        sendEvent(name: "contact", value: verbs.get(2), isStateChange: true)
		sendEvent(name: "door", value: verbs.get(1), isStateChange: true)
		sendUpdateEvent()
	} else {
		parent.log("Child door ${id} (${device.getLabel()}) is already ${verbs.get(2)}.","debug")
	}		
}


public setDoor(String strValue) {
     if (strValue != device.currentValue("door")) {
         def verbs = getVerbs(strValue)
        	if (!verbs) {
 	    	parent.log("Invalid action ${strValue} submitted to door.","error")
 		    return
 	    }
		def process = true
 		if (state.lastUpdated == null) {
			parent.log("lastUpdated state value is null.","debug")
			state.lastUpdated = (new Date()).format("yyyy-MM-dd HH:mm:ss")
 		} else {
 			if (device.currentValue("door") == "opening" || device.currentValue("door") == "closing" ) {
				parent.log("Currently transitioning between states, need to see if I should wait.","debug")
				def now = (new Date())
				def last = (new Date()).parse("yyyy-MM-dd HH:mm:ss",state.lastUpdated)
				use (groovy.time.TimeCategory) {
 					def duration = TimeCategory.minus(now, last)
					if (duration.toMilliseconds()/1000 < nvl(transitionInterval,45)) {
						process = false
					}
 				}

				if (!process) {
					parent.log("Transition time started ${last}","trace")
					parent.log("Time is now ${now}","trace")
					parent.log("Ignoring door status during transition time... skipping","debug")
					return
				}
 				parent.log("Waited for door to transition.  Door now reports ${strValue}.","info")
 				if (!verbs.contains(device.currentValue("door"))) {
 					parent.log("Door went from ${device.currentValue("door")} to ${strValue} when it shouldn't have.","error")
 				} else {
 					parent.log("Door went from ${device.currentValue("door")} to ${strValue}.","debug")
 				}
 			}
 		}
		parent.log("Door has changed states from ${device.currentValue("door")} to ${strValue}.","info")
		sendEvent(name: "switch", value: verbs.get(3), isStateChange: true)
		sendEvent(name: "contact", value: verbs.get(2), isStateChange: true)
		sendEvent(name: "door", value: verbs.get(0), isStateChange: true)
		sendUpdateEvent ()
    }
}

public setBattery(String strValue) {
    if (strValue != device.currentValue("battery")) {
		parent.log("Battery has changed states from ${device.currentValue("battery")} to ${strValue}.","info")
        sendEvent(name: "battery", value: strValue, isStateChange: true)
        sendUpdateEvent ()
    }
}

def sendUpdateEvent() {
   		def date = new Date().format("yyyy-MM-dd HH:mm:ss")
		state.lastUpdated = date
		sendEvent(name: "lastUpdated", value: date.toString(), displayed: false)
}

def nvl(value, alternateValue) {
    if (value == null)
        return alternateValue;

    return value;
}