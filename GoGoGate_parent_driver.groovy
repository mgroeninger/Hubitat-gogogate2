/**
    This is the parent driver for the composite GoGoGate 2 driver for Hubitat
    The most recent version is available at:
        https://raw.githubusercontent.com/mgroeninger/Hubitat-gogogate2/master/GoGoGate_parent_driver.groovy
    
    For more documentation please see https://github.com/mgroeninger/Hubitat-gogogate2
**/

def version() {"v0.01"}
def childType() {"GoGoGate 2 Child"}

metadata {
    definition (name: "GoGoGate 2 Parent", namespace: "gogogate2-composite", author: "Matt Groeninger") {
        capability "Initialize"
        capability "Refresh"
        capability "Light"
		capability "Switch"
		
		attribute "lastSettingSave", "String"

		command "on"
		command "off"
        command "recreateChildDevices"
        
    }
}

preferences {
    input "deviceIP", "text", title: "GoGoGate 2 IP Address", description: "in form of 192.168.1.138", required: true, displayDuringSetup: true
	input "username", "text", title: "GoGoGate 2 Username", description: /Username to connect with - default is "admin"/, defaultValue: "admin", required: true, displayDuringSetup: true
	input "pass", "password", title: "GoGoGate 2 Password", description: "User's password", required: true, displayDuringSetup: true
    input "pollingInterval", "number", title: "Polling Interval", description: "in seconds", range: "2..30", defaultValue: 10, displayDuringSetup: true
    input "logging", "enum", title: "Log Level", required: false, defaultValue: "INFO", options: ["TRACE", "DEBUG", "INFO", "WARN", "ERROR"]
	input "recreateDevices", "bool", title: "Recreate child devices on settings update?", defaultValue: false 
}

def uninstalled() {
	deleteChildren()
}

def installed() {
    log("Device installed.","debug")
    state.configured = false
    state.childrencreated = false
    state.version = version()
	state.cookie = null
    updateSaveTime()
}

def initialize() {
    log("Clearing settings.","info")
    state.version = version()
	state.cookie = null
    sendEvent(name: "switch", value: "unknown", isStateChange: true)
    unschedule()
    testConfig()
    updateSaveTime()
	if (!state.configured) return
	if (!state.childrencreated) {
	    log("State 'childrencreated' is ${state.childrencreated} so we create new children.","trace")
    	recreateChildDevices()
	}
    setupPolling()
    runEvery5Minutes(refreshCookie)
}

def refresh() {
    log("Refresh tick","debug")
    getControllerInfo()
}

def updated() {
    updateSaveTime()
    log("Updating settings...","info")
    testConfig()
    if (!state.configured) return 
    unschedule()
    log("Polling unscheduled","debug")
	if (recreateDevices || (state.configured && !state.childrencreated)) {
         log("Recreating devices","debug")
	     recreateChildDevices()
	}
    if (state.configured) {
         runEvery5Minutes(refreshCookie)
         log("Cookie refresh scheduled.","debug")
         log("Scheduling polling","debug")
         setupPolling()
    }
}

private updateSaveTime() {
    def nowDay = new Date().format("MMM dd", location.timeZone)
    def nowTime = new Date().format("h:mm a", location.timeZone)
    log("Settings updated ${nowDay}, ${nowTime}","debug")
    sendEvent(name: "lastSettingSave", value: "${nowDay}, ${nowTime}", displayed: false)
}

private determineLogLevel(data) {
    switch (data?.toUpperCase()) {
        case "TRACE":
            return 0
            break
        case "DEBUG":
            return 1
            break
        case "INFO":
            return 2
            break
        case "WARN":
            return 3
            break
        case "ERROR":
        	return 4
            break
        default:
            return 1
    }
}

def log(data, type) {
    data = "GoGoGate2 -- ${data ?: ''}"
    if (determineLogLevel(type) >= determineLogLevel(logging ?: "INFO")) {
        switch (type?.toUpperCase()) {
            case "TRACE":
                log.trace "${data}"
                break
            case "DEBUG":
                log.debug "${data}"
                break
            case "INFO":
                log.info "${data}"
                break
            case "WARN":
                log.warn "${data}"
                break
            case "ERROR":
                log.error "${data}"
                break
            default:
                log.error "GoGoGate2 -- ${device.label} -- Invalid Log Setting"
        }
    }
}

def testOptions(){
    if (!deviceIP || !username || !pass) {
        log("Please complete the required fields to connect to your GoGoGate 2 device.","error")
        false
    }
    true
}

def testConfig() {
    if (testOptions()) {
        refreshCookie()
        if (state.configured) {
            log("Everything appears to be configured and the HTTP request was successful.","info")
        } else {
            log("Everything appears to be configured but a request was not successful","error")
        }
    } 
    false
}

def void setupPolling() {
	def Integer max = (60/pollingInterval)
    def Integer waitPeriod = pollingInterval*1000
	log("Setting wait time to ${pollingInterval} seconds (${waitPeriod} ms) and will poll ${max} times per minute.","info")
  	runEvery1Minute(pollDevice)
	runIn(5,pollDevice)
}

def pollDevice() {
    def Integer max = (60/pollingInterval)
    def Integer waitPeriod = pollingInterval*1000
    for(int i = 0;i<max;i++) {
       	log("Loop ${i+1} for pollDevice","trace")
        refresh()
    	pauseExecution(waitPeriod)
    }
}

def recreateChildDevices() {
    log("Parent recreateChildDevices","debug")
    deleteChildren()
    createChildDevices()
}

def createChildDevices() {
    def createdCount = 0
    log("Creating child devices","trace")
    for (i in 1..3) {
        def (temp, battery) =  getSensorInfo(i)
        if (!temp || temp == "-1000000") {
            log("Sensor ${i} appears invalid.","trace")
		} else {
			log("Sensor ${i} responded with ${temp}; appears to be valid.","debug")
            if (createChildDevice("${i}")) {
                createdCount+=1
            }
		}
    }
    if (createdCount) { 
		log("Created ${createdCount} child devices for garage doors.","info")
	    state.childrencreated = true
	}
}

private createChildDevice(String doorNumber) {
    log("Attempting to create child for door number $doorNumber.","trace")
    try {
        addChildDevice(childType(), "${device.deviceNetworkId}-child-$doorNumber",[name: "door-$doorNumber", label: "$device.displayName door $doorNumber", isComponent: true])
        log("Created child device with network id: ${device.deviceNetworkId}-child-${doorNumber}}","trace")
        return true
    } catch(e) {
        log("Failed to create child device with error = ${e}","error")
        return false
    }
}

def deleteChildren() {
	log("Parent deleteChildren","debug")
	state.childrencreated = false
	def children = getChildDevices()
    children.each {child->
  		deleteChildDevice(child.deviceNetworkId)
    }
}

def on() {
	if (testCookie()) {
		params = [
			uri: "http://${deviceIP}/isg/light.php?op=activate&light=0",
			headers: 
				["Cookie": """${state.cookie}""",
				"Referer": "http://${deviceIP}/index.php",
				"Host": """${deviceIP}""",
				"Connection": "keep-alive"],
			requestContentType: "application/json; charset=UTF-8"]
		log("Turning light on.","info")
		getInfo(params)
		sendEvent(name: "switch", value: "on", isStateChange: true)
	}
}

def off() {
	if (testCookie()) {
		params = [
			uri: "http://${deviceIP}/isg/light.php?op=activate&light=1",
			headers: 
				["Cookie": """${state.cookie}""",
				"Referer": "http://${deviceIP}/index.php",
				"Host": """${deviceIP}""",
				"Connection": "keep-alive"],
			requestContentType: "application/json; charset=UTF-8"]
		log("Turning light off.","info")
		getInfo(params)
		sendEvent(name: "switch", value: "off", isStateChange: true)
	}
}

def toggleDoor(Integer id) {
	if (testCookie()) {
		params = [
			uri: "http://${deviceIP}/isg/opendoor.php?numdoor=${id}&status=0&login=${username}",
			headers: 
				["Cookie": """${state.cookie}""",
				"Referer": "http://${deviceIP}/index.php",
				"Host": """${deviceIP}""",
				"Connection": "keep-alive"],
			requestContentType: "application/json; charset=UTF-8"]
		log("Telling door to start.","trace")
		getInfo(params)
	}
}

def getControllerInfo() {
    lightStatus = getLightStatus()
	if (device.currentValue("switch") != lightStatus) {
		log("Light has changed states from ${device.currentValue("switch")} to ${lightStatus}.","info")
        sendEvent(name: "switch", value: lightStatus, isStateChange: true)
    }
	doorStateArr = getDoorStatus()
   	def children = getChildDevices()
    children.each { child->
        currentDoor = child.deviceNetworkId[-1] as Integer
        currentDoorStateInt = doorStateArr.get(currentDoor-1) as Integer
        if (currentDoorStateInt) {
            currentDoorDesc = "open"
        } else {
            currentDoorDesc = "closed"
        }
        log("Door ${currentDoor} has returned ${currentDoorStateInt}, which indicates the door is ${currentDoorDesc}.","trace")
		try {
			child.setDoor(currentDoorDesc)
			} 
        catch(e) {
            log("Child parse call failed: ${e}","error")
        }
        def (temp, battery) = getSensorInfo(currentDoor)
        log("Sensor ${currentDoor} indicates the temperature is ${temp} and the battery level is ${battery}.","debug")
        child.setTemperature(temp)
        child.setBattery(battery)
    }
    if (lightStatus == "unknown" || doorStateArr == null) {
        log("Multiple connection failures. Possible misconfiguration of device connection information.", "error")
    }
}

def getLightStatus() {
	if (testCookie()) {
        def params = [
            uri: "http://${deviceIP}/isg/light.php?op=refresh",
            headers: 
                ["Cookie": """${state.cookie}""",
                "Referer": "http://${deviceIP}/index.php",
                "Host": """${deviceIP}""",
                "Connection": "keep-alive"],
            requestContentType:
				"application/json; charset=UTF-8"]
        resp = getInfo(params)		
		if (resp) {
			log("Raw light status info is: ${resp}","trace")
            if (resp.text() as Integer) {
       			log("Light status is on","debug")
                return "on"
            } else {
       			log("Light status is off","debug")
                return "off"
            }
		} else {
			log("Unable to get light data. Possible misconfiguration of device connection information.","warn")
            return "unknown"
		}
    }
}

def getDoorStatus(){
    if (testCookie()) {
        def params = [
            uri: "http://${deviceIP}/isg/statusDoorAll.php?status1=10",
            headers: 
                ["Cookie": """${state.cookie}""",
                "Referer": "http://${deviceIP}/index.php",
                "Host": """${deviceIP}""",
                "Connection": "keep-alive"],
			requestContentType:
				"application/json; charset=UTF-8"]
		resp = getInfo(params)
		if (resp && (resp.toString()) ) {
			log("Raw status info is: '${resp.toString()}'","debug")
			door1 = parse(resp.toString(),0)
			door2 = parse(resp.toString(),1)
			door3 = parse(resp.toString(),2)
            [ door1, door2, door3 ]
		} else {
			log("Unable to get door status data. Possible misconfiguration of device connection information.","warn")
		}
    }
}

def getSensorInfo(int sensor) {
    if (testCookie()) {
        params = [
            uri: "http://${deviceIP}/isg/temperature.php?door=${sensor}",
            headers: 
                ["Cookie": """${state.cookie}""",
                	"Referer": "http://${deviceIP}/index.php",
                	"Host": """${deviceIP}""",
                	"Connection": "keep-alive"],
         	   requestContentType: "application/json; charset=UTF-8"]
        resp = getInfo(params)
		if (resp) {
			log("Raw sensor info is: ${resp}","debug")
			temp = ((parse(resp.toString(),0)) as Integer)/1000
			battery = parse(resp.toString(),1)
            log("Converting temp to system scale","trace")
            reportTemp = convertTemperatureIfNeeded(temp, "C", 1)
            reportTemp = ("${reportTemp} \u00b0" + getTemperatureScale()) as String            
            if (temp > -40 && temp < 60) {
       			[ reportTemp, battery ]
            } else {
                log("Invalid sensor temperature of ${reportTemp} .","warn")
                [ false, false]
            }			
		} else {
			log("Unable to get sensor data. Possible misconfiguration of device connection information or sensor connection problem.","warn")
			[ false, false]
		}
    }
}

def getInfo(params) {
    if (state.configured) {
        try{
            httpGet(params){response ->
                if(response.status != 200 ) {
                    log("Received HTTP error ${response.status}. Check your IP Address and GoGoGate 2 device!","error")
					return false
                } else if (response.data == "Restricted Access") {
                    log("Received invalid data of ${response.data}. Check your IP Address and GoGoGate 2 device!","error")
					return false
                } else {
                    log("Response from GoGoGate 2 was: ${response.data}","debug")
					return response.data
                }
            }
        } catch (Exception e) {
        	log("GoGoGate 2 returned: ${e}","error")
			return false
        } 
    }
}

def testCookie() {
    if (state.cookie == null) {
        log("state.cookie is ${state.cookie}","debug")
        return state.configured
    }
    true
}

def refreshCookie(){
	def allcookie
	def cookie
    log("Refreshing the cookie","trace")
    if (testOptions()) {
        params = [
            uri: "http://${deviceIP}",
            body: "login=${username}&pass=${pass}&send-login=Sign+In",
            headers: 
                ["Host": """${deviceIP}""",
                "Connection": "keep-alive"]
        ]
        try{
	        httpPost(params) {
                response ->
					if(response.status != 200) {
					 log("Received HTTP error ${response.status}. Check your IP Address and GoGoGate 2 device.","error")
						state.cookie = null
						state.configured = false
					} else if (response.data == "Restricted Access") {
					 	log("Received invalid data of ${response.data}. Check your IP Address and GoGoGate 2 device.","error")
						state.cookie = null
						state.configured = false
					} else if (response.data.toString().contains("Wrong login or password.")) {
						log("Received response but did not appear to be logged in. Check username and password.","error")
						state.cookie = null
						state.configured = false
					} else {
						resp = allcookie = response.headers['Set-Cookie']
						cookie = allcookie.toString().replaceAll("; path=/","").replaceAll("Set-Cookie: ","")
						log("Basic request to set cookie returned: ${cookie}","debug")
						state.cookie = cookie
						state.configured = true
					}
            }
        } catch (Exception e) {
            log("GoGoGate 2 returned: ${e}","error")
            false
        }
    } 
}

def parse(String jsonText, int i) {
	//the data being passed in is in json format, but lacks description labels, so we parse it positionally
    def json = null;
    try{
        json = new groovy.json.JsonSlurper().parseText(jsonText)
        if(json == null){
            log("Data not parsed","warn")
            return
        }
    }  catch(e) {
        log("Failed to parse json e = ${e}","error")
        return
    }
    return json[i]
}