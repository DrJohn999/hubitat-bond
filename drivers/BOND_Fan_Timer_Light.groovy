/**
 *  BOND Fan Timer Light
 *
 *  Copyright 2019 Dominick Meglio
 *
 */

metadata {
    definition (name: "BOND Fan Timer Light", namespace: "bond", author: "dmeglio@gmail.com") {
		capability "Switch"
        capability "Light"

		
		command "dim", ["number"]
		command "startDimming"
		command "stopDimming"
    }
}

def dim(duration) {
	parent.handleDim(device, device.deviceNetworkId.split(":")[1], duration)
}

def startDimming() {
	parent.handleStartDimming(device, device.deviceNetworkId.split(":")[1])
}

def stopDimming() {
	parent.handleStopDimming(device, device.deviceNetworkId.split(":")[1])
}

def on() {
	parent.handleLightOn(device, device.deviceNetworkId.split(":")[1])
}

def off() {
    parent.handleLightOff(device, device.deviceNetworkId.split(":")[1])
}

def installed() {
	sendEvent(name: "numberOfButtons", value: "1")
}

def updated() {
	sendEvent(name: "numberOfButtons", value: "1")
}