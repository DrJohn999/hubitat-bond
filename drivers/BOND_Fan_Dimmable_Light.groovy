/**
 *  BOND Fan Dimmable Light
 *
 *  Copyright 2019 Dominick Meglio
 *
 */

metadata {
    definition (name: "BOND Fan Dimmable Light", namespace: "bond", author: "dmeglio@gmail.com") {
		capability "SwitchLevel"
		capability "Switch"
        capability "Light"
    }
	
	preferences {
        input name: "dimmerTime", type: "number", title: "Dimmer Duration", description: "If your fan requires you to hold the button to dim, specify the total number of seconds you must hold the button to go from 100% to 0%.", required: false, defaultValue: 30
    }
}

def setLevel(level, duration) {
	parent.handleLightLevel(device, device.deviceNetworkId.split(":")[1], level)
}
def setLevel(level) {
	parent.handleLightLevel(device, device.deviceNetworkId.split(":")[1], level)
}

def on() {
	parent.handleLightOn(device, device.deviceNetworkId.split(":")[1])
}

def off() {
    parent.handleLightOff(device, device.deviceNetworkId.split(":")[1])
}

def installed() {
  updateDataValue("dimmerTime", dimmerTime.toString())
}

def updated() {
  updateDataValue("dimmerTime", dimmerTime.toString())
}