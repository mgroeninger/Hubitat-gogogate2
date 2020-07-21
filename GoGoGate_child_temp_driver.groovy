/**
    This is the child driver for the composite GoGoGate 2 driver for Hubitat
    The most recent version is available at:
        https://raw.githubusercontent.com/mgroeninger/Hubitat-gogogate2/master/GoGoGate_child_temp_driver.groovy
    
    For more documentation please see https://github.com/mgroeninger/Hubitat-gogogate2
**/
import groovy.time.*

metadata {
    definition (name: "GoGoGate 2 Temperature Child", namespace: "gogogate2-composite", author: "Matt Groeninger") {
		capability "Temperature Measurement"
		capability "Sensor"
		
		attribute "lastUpdated", "string"
		attribute "battery", "string"

    }
}

def initialize() {
	sendUpdateEvent()
}

public setBattery(String strValue) {
    if (strValue != device.currentValue("battery")) {
		parent.log("Battery has changed states from ${device.currentValue("battery")} to ${strValue}.","info")
        sendEvent(name: "battery", value: strValue, isStateChange: true)
        sendUpdateEvent ()
    }
}

public setTemperature(String strValue) {
	def (temp, String scale) = strValue.split()
	temp = temp as double
    if (temp != device.currentValue("temperature")) {
		parent.log("Temperature has changed from '${device.currentValue("temperature")}' to '${temp}'.","info")
        sendEvent(name: "temperature", value: temp, unit: scale, isStateChange: true)
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