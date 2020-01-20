Composite Driver for GoGoGate2 management in Hubitat
=======

This is a composite driver for GoGoGate2 management in Hubitat.  Features are:
* Parent driver installs for GoGoGate device endpoint itself  This allows:
    * Multiple door devices to detected/installed for a single GoGoGate2 (up to the supported three)
    * The lights to be controlled
    * Minimizes http requests to the device for polling
* Child devices present doors as both door controls and switch controls, making Alexa integration simplier
* Child devices report temperature and battery readings for each sensor

Installation
1. Add the child device driver to Drivers Code on your Hubitat hub from https://raw.githubusercontent.com/mgroeninger/Hubitat-gogogate2/master/GoGoGate_child_driver.groovy
2. Add the parent device driver to Drivers Code on your Hubitat hub from https://raw.githubusercontent.com/mgroeninger/Hubitat-gogogate2/master/GoGoGate_parent_driver.groovy
3. Create a Virtual Device (under devices) of type "GoGoGate 2 Parent"
4. Fill in the ip address of your GoGoGate2 device, as well as the username and password you use to access it and save the preferences
5. Optionally, change the child device labels to name you would like to use

Note:
The driver simulates "opening" and "closing" states when a door is opened or closed using Hubitat but does not have any way to simulate those states when a door is opened or closed with the door button itself. 

Credits/Development Progression:
* Started with basic composite plugin framework from https://github.com/dpark/Hubitat-Composite-Driver
* Relied heavily on https://github.com/bmergner/bcsmart/tree/Sensor-Update for polling logic, URIs and the parse function
* Used log output control from https://github.com/stephack/Hubitat/blob/master/drivers/Lifx%20Group%20of%20Groups/Lifx%20GoG.groovy
