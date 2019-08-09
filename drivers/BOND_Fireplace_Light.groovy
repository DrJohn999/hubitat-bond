/**
 *  BOND Fireplace Light
 *
 *  Copyright 2019 Dominick Meglio
 *
 */
 
metadata {
    definition (name: "BOND Fireplace Light", namespace: "bond", author: "dmeglio@gmail.com") {
		capability "Switch"
        capability "Light"
    }
}

def on() {
    
	parent.handleLightOn(device, device.deviceNetworkId.split(":")[1])
}

def off() {
	parent.handleLightOff(device, device.deviceNetworkId.split(":")[1])
}

