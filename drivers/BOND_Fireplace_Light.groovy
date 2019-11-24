/**
 *  BOND Fireplace Light
 *
 *  Copyright 2019 Dominick Meglio
 *
 */
 
metadata {
    definition (
		name: "BOND Fireplace Light", 
		namespace: "bond", 
		author: "dmeglio@gmail.com",
		importUrl: "https://raw.githubusercontent.com/dcmeglio/hubitat-bond/master/drivers/BOND_Fireplace_Light.groovy"
	) {
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

