/**
    This is the parent driver for the composite GoGoGate 2 driver for Hubitat
    The most recent version is available at:
        https://raw.githubusercontent.com/mgroeninger/Hubitat-gogogate2/master/GoGoGate_child_driver.groovy
    
    For more documentation please see https://github.com/mgroeninger/Hubitat-gogogate2
**/

metadata {
    definition (name: "GoGoGate 2 Child", namespace: "gogogate2-composite", author: "Matt Groeninger") {
 		capability "Door Control"
		capability "Garage Door Control"
		capability "Temperature Measurement"
		capability "Sensor"
		capability "Switch"

		attribute "lastUpdated", "string"
		attribute "battery", "string"
		
		//command set
		command "open"
        command "close"
		command "on"
		command "off"
    }
}

preferences {
    input "transitionInterval", "number", title: "Door Transition Interval", description: "Duration to ignore door state while opening/closing (in seconds)", range: "2..60", defaultValue: 45
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

def getVerbs(String key) {
        def verbs = [
            "open": ["open", "opening", "open","on"], 
            "opened": ["opened", "opening", "open","on"], 
            "close":["close", "closing", "closed","off"],
            "closed":["closed", "closing", "close","off"]
        ]
        verbs.get(key) ?: false
    }

def cmdToggle(String action) {
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
		sendEvent(name: "door", value: verbs.get(1), isStateChange: true)
		sendUpdateEvent()
	} else {
		parent.log("Child door ${id} (${device.getLabel()}) is already ${verbs.get(2)}.","debug")
	}		
}


def setDoor(String strValue) {
    if (strValue != device.currentValue("door")) {
        def verbs = getVerbs(strValue)
       	if (!verbs) {
	    	parent.log("Invalid action ${strValue} submitted to door.","error")
		    return
	    }
		if (device.currentValue("door") == "opening" || device.currentValue("door") == "closing" ) {
   			def now = new Date()
			def then = new Date(device.currentValue("lastUpdated"))
			duration = groovy.time.TimeCategory.minus(now,then)
			if (duration.toMilliseconds() < (nvl(transitionInterval,45)*1000)) {
				parent.log("Ignoring door status during transition time: at ${duration} of ${transitionInterval} seconds","debug")
				return
			} else {
				parent.log("Waited ${transitionInterval} seconds for door to transition.  Door now reports ${strValue}.","info")
				if (!verbs.contains(device.currentValue("door"))) {
					parent.log("Door went from ${device.currentValue("door")} to ${strValue} when it shouldn't have.","error")
				} else {
					parent.log("Door went from ${device.currentValue("door")} to ${strValue}.","debug")
				}
			}
		}
		parent.log("Door has changed states from ${device.currentValue("door")} to ${strValue}.","info")
        sendEvent(name: "switch", value: verbs.get(3), isStateChange: true)
		sendEvent(name: "door", value: verbs.get(0), isStateChange: true)
        sendUpdateEvent ()
   }
}


def setBattery(String strValue) {
    if (strValue != device.currentValue("battery")) {
		parent.log("Battery has changed states from ${device.currentValue("battery")} to ${strValue}.","info")
        sendEvent(name: "battery", value: strValue, isStateChange: true)
        sendUpdateEvent ()
    }
}

def setTemperature(String strValue) {
	def (temp, String scale) = strValue.split()
	temp = temp as double
    if (temp != device.currentValue("temperature")) {
		parent.log("Temperature has changed from '${device.currentValue("temperature")}' to '${temp}'.","info")
        sendEvent(name: "temperature", value: temp, unit: scale, isStateChange: true)
        sendUpdateEvent ()
    }
}

def sendUpdateEvent() {
   		def date = new Date()
		sendEvent(name: "lastUpdated", value: date.toString(), displayed: false)
}

def nvl(value, alternateValue) {
    if (value == null)
        return alternateValue;

    return value;
}