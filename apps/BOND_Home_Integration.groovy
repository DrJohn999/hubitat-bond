/**
 *  BOND Home Integration
 *
 *  Copyright 2019 Dominick Meglio
 *
 */

definition(
    name: "BOND Home Integration",
    namespace: "dcm.bond",
    author: "Dominick Meglio",
    description: "Connects to BOND Home hub",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
	page(name: "prefHub", title: "BOND")
	page(name: "prefListDevices", title: "BOND")
	page(name: "prefPowerSensors", title: "BOND")
}

def prefHub() {
	return dynamicPage(name: "prefHub", title: "Connect to BOND", nextPage:"prefListDevices", uninstall:false, install: false) {
		section("Hub Information"){
			input("hubIp", "text", title: "BOND Hub IP", description: "BOND Hub IP Address")
			input("hubToken", "text", title: "BOND Hub Token", description: "BOND Hub Token")
            input("debugOutput", "bool", title: "Enable debug logging?", defaultValue: true, displayDuringSetup: false, required: false)
		}
	}
}

def prefListDevices() {
	getDevices();
	return dynamicPage(name: "prefListDevices", title: "Devices", nextPage: "prefPowerSensors", install: false, uninstall: false) {
		section("Devices") {
			if (state.fireplaceList.size() > 0)
				input(name: "fireplaces", type: "enum", title: "Fireplaces", required:false, multiple:true, options:state.fireplaceList, hideWhenEmpty: true)
			if (state.fanList.size() > 0)
				input(name: "fans", type: "enum", title: "Fans", required:false, multiple:true, options:state.fanList, hideWhenEmpty: true)
			if (state.shadeList.size() > 0)
				input(name: "shades", type: "enum", title: "Shades", required:false, multiple:true, options:state.shadeList, hideWhenEmpty: true)
		}
	}
}

def prefPowerSensors() {
	return dynamicPage(name: "prefPowerSensors", title: "Fireplace Power Meters", install: true, uninstall: true, hideWhenEmpty: true) {
		section("Fireplace Power Meters") {
			paragraph "For each fireplace device you can associate a power meter to more accurately tell when it is powered on"
			if (fireplaces != null) {
				for (def i = 0; i < fireplaces.size(); i++) {
					input(name: "fireplaceSensor${i}", type: "capability.powerMeter", title: "Sensor for ${state.fireplaceList[fireplaces[i]]}", required: false, submitOnChange: true)
				}
				for (def i = 0; i < fireplaces.size(); i++) {
					if (this.getProperty("fireplaceSensor${i}") != null)
					input(name: "fireplaceSensorThreshold${i}", type: "number", title: "Sensor threshold for ${state.fireplaceList[fireplaces[i]]}", required: false)
				}
			}
		}
	}
}

def installed() {
	logDebug "Installed with settings: ${settings}"

	initialize()
}

def updated() {
	logDebug "Updated with settings: ${settings}"
    unschedule()
	unsubscribe()
	initialize()
}

def uninstalled() {
	logDebug "Uninstalled app"

	for (device in getChildDevices())
	{
		deleteChildDevice(device.deviceNetworkId)
	}	
}

def initialize() {
	logDebug "initializing"

	cleanupChildDevices()
	createChildDevices()
	subscribeSensorEvents()	
    schedule("0/30 * * * * ? *", updateDevices)
}

def getDevices() {
	state.fireplaceList = [:]
    state.fireplaceDetails = [:]
	state.fireplaceProperties = [:]
	state.fanList = [:]
    state.fanDetails = [:]
	state.fanProperties = [:]
	state.shadeList = [:]
	state.shadeDetails = [:]
	state.shadeProperties = [:]
	state.deviceList = [:]
	def params = [
		uri: "http://${hubIp}",
		path: "/v2/devices",
		contentType: "application/json",
		headers: [ 'BOND-Token': hubToken ]
	]
	try
	{
		httpGet(params) { resp ->
			if (checkHttpResponse("getDevices", resp))
			{
				for (deviceid in resp.data) {
					if (deviceid.key == "_")
						continue
					getDeviceById(deviceid);
				}
			}
		}
	}
	catch (e)
	{
		log.debug "HTTP Exception Received on GET: $e"
	}
}

def getDeviceById(id) {
	def params = [
		uri: "http://${hubIp}",
		path: "/v2/devices/${id}",
		contentType: "application/json",
		headers: [ 'BOND-Token': hubToken ]
	]
	try
	{
		httpGet(params) { resp ->
			if (checkHttpResponse("getDeviceById", resp))
			{
				if (resp.data.type == "FP")
				{
					state.fireplaceList[id.key] = resp.data.name
					state.fireplaceDetails[id.key] = resp.data.actions
					state.fireplaceProperties[id.key] = getDeviceProperties(id)
				}
				else if (resp.data.type == "CF")
				{
					state.fanList[id.key] = resp.data.name
					state.fanDetails[id.key] = resp.data.actions
					state.fanProperties[id.key] = getDeviceProperties(id)
				}
				else if (resp.data.type == "MS")
				{
					state.shadeList[id.key] = resp.data.name
					state.shadeDetails[id.key] = resp.data.actions
					state.shadeProperties[id.key] = getDeviceProperties(id)
				}
			}
		}
	}
	catch (e)
	{
		log.debug "HTTP Exception Received on GET: $e"
	}
}

def getDeviceProperties(id) {
	def params = [
		uri: "http://${hubIp}",
		path: "/v2/devices/${id}/properties",
		contentType: "application/json",
		headers: [ 'BOND-Token': hubToken ]
	]
	def result = null
	try
	{
		httpGet(params) { resp ->
			if (checkHttpResponse("getDeviceProperties", resp))
			{
				result = resp.data
			}
		}
	}
	catch (e)
	{
		log.debug "HTTP Exception Received on GET: $e"
	}
	return result
}

def createChildDevices() {
	if (fireplaces != null) 
	{
		for (fireplace in fireplaces)
		{
			def fpDevice = getChildDevice("bond:" + fireplace)
			if (!fpDevice)
            {
				fpDevice = addChildDevice("bond", "BOND Fireplace", "bond:" + fireplace, 1234, ["name": state.fireplaceList[fireplace], isComponent: false])\
			}
			if (state.fireplaceDetails[fireplace].contains("TurnFpFanOn"))
			{
				if (!fpDevice.getChildDevice("bond:" + fireplace + ":fan"))
					fpDevice.addChildDevice("bond", "BOND Fireplace Fan", "bond:" + fireplace + ":fan", ["name": state.fireplaceList[fireplace] + " Fan", isComponent: true])
			}
			if (state.fireplaceDetails[fireplace].contains("TurnLightOn"))
			{
				if (!fpDevice.getChildDevice("bond:" + fireplace + ":light"))
					fpDevice.addChildDevice("bond", "BOND Fireplace Light", "bond:" + fireplace + ":light", ["name": state.fireplaceList[fireplace] + " Light", isComponent: true])
			}
		}
	}
	
	if (fans != null) 
	{
		for (fan in fans)
		{
			def fanDevice = getChildDevice("bond:" + fan)
			if (!fanDevice)
            {
				if (state.fanDetails[fan].contains("SetDirection"))
					fanDevice = addChildDevice("bond", "BOND Fan With Direction", "bond:" + fan, 1234, ["name": state.fanList[fan], isComponent: false])
				else
					fanDevice = addChildDevice("bond", "BOND Fan", "bond:" + fan, 1234, ["name": state.fanList[fan], isComponent: false])
			}
			if (state.fanDetails[fan].contains("TurnUpLightOn") && state.fanDetails[fan].contains("TurnDownLightOn"))
			{
				if (state.fanDetails[fan].contains("SetUpLightBrightness") && state.fanDetails[fan].contains("SetDownLightBrightness"))
				{
					if (!fanDevice.getChildDevice("bond:" + fan + ":uplight"))
						fanDevice.addChildDevice("bond", "BOND Fan Dimmable Light", "bond:" + fan + ":uplight", ["name": state.fanList[fan] + " Up Light", isComponent: true])
					if (!fanDevice.getChildDevice("bond:" + fan + ":downlight"))
						fanDevice.addChildDevice("bond", "BOND Fan Dimmable Light", "bond:" + fan + ":downlight", ["name": state.fanList[fan] + " Down Light", isComponent: true])

				}
				else
				{
					if (!fanDevice.getChildDevice("bond:" + fan + ":uplight"))
						fanDevice.addChildDevice("bond", "BOND Fan Light", "bond:" + fan + ":uplight", ["name": state.fanList[fan] + " Up Light", isComponent: true])
					if (!fanDevice.getChildDevice("bond:" + fan + ":downlight"))
						fanDevice.addChildDevice("bond", "BOND Fan Light", "bond:" + fan + ":downlight", ["name": state.fanList[fan] + " Down Light", isComponent: true])
				}
			}
			else if (state.fanDetails[fan].contains("TurnLightOn"))
			{
				if (!fanDevice.getChildDevice("bond:" + fan + ":light"))
				{
					if (state.fanDetails[fan].contains("SetBrightness"))
					{
						fanDevice.addChildDevice("bond", "BOND Fan Dimmable Light", "bond:" + fan + ":light", ["name": state.fanList[fan] + " Light", isComponent: true])
					}
					else
						fanDevice.addChildDevice("bond", "BOND Fan Light", "bond:" + fan + ":light", ["name": state.fanList[fan] + " Light", isComponent: true])
				}
			}
		}
	}
	
	if (shades != null)
	{
		for (shade in shades)
		{
			def shadeDevice = getChildDevice("bond:" + shade)
			if (!shadeDevice)
            {
				shadeDevice = addChildDevice("bond", "BOND Motorized Shade", "bond:" + shade, 1234, ["name": state.shadeList[shade], isComponent: false])
			}
		}
	}
}

def cleanupChildDevices()
{
	for (device in getChildDevices())
	{
		def deviceId = device.deviceNetworkId.replace("bond:","")
		
		def deviceFound = false
		for (fireplace in fireplaces)
		{
			if (fireplace == deviceId)
			{
				deviceFound = true
				cleanupFPComponents(device, fireplace)
				break
			}
		}
		
		if (deviceFound == true)
			continue
		
		for (fan in fans)
		{
			if (fan == deviceId)
			{
				deviceFound = true
				cleanupFanComponents(device, fan)
				break
			}
		}
		if (deviceFound == true)
			continue
			
		for (shade in shades)
		{
			if (shade == deviceId)
			{
				deviceFound = true
				break
			}
		}
		if (deviceFound == true)
			continue
		
		deleteChildDevice(device.deviceNetworkId)
	}
}

def cleanupFPComponents(device, fireplace)
{
	if (!state.fireplaceDetails[fireplace].contains("TurnFpFanOn"))
	{
		device.deleteChildDevice("bond:" + fireplace + ":fan")
	}
	if (!state.fireplaceDetails[fireplace].contains("TurnLightOn"))
	{
		device.deleteChildDevice("bond:" + fireplace + ":light")
	}
}

def cleanupFanComponents(device, fan)
{
	if (!state.fanDetails[fan].contains("TurnUpLightOn") || !state.fanDetails[fan].contains("TurnDownLightOn"))
	{
		device.deleteChildDevice("bond:" + fan + ":uplight")
		device.deleteChildDevice("bond:" + fan + ":downlight")
	}
	if (!state.fanDetails[fan].contains("TurnLightOn") || (state.fanDetails[fan].contains("TurnUpLightOn") && state.fanDetails[fan].contains("TurnDownLightOn")))
	{
		device.deleteChildDevice("bond:" + fan + ":light")
	}
}

def subscribeSensorEvents() {
	if (fireplaces != null)
	{
		for (def i = 0; i < fireplaces.size(); i++)
		{
			def sensorDevice = this.getProperty("fireplaceSensor${i}")
			if (sensorDevice != null)
			{
				logDebug "subscribing to power event for ${sensorDevice}"
				subscribe(sensorDevice, "power", powerMeterEventHandler)
			}
		}
	}
}
				  
def powerMeterEventHandler(evt) {
	logDebug "Received power meter event ${evt}"
	for (def i = 0; i < fireplaces.size(); i++)
	{
		def sensorDevice = this.getProperty("fireplaceSensor${i}")
		if (evt.device.id == sensorDevice.id)
		{
			def fireplace = fireplaces[i];
			def fireplaceDevice = getChildDevice("bond:" + fireplace)
			def threshold = 10
			def value = "on"
			if (evt.integerValue < threshold)
				value = "off"
			if (value != fireplaceDevice.currentValue("switch"))
			{
				logDebug "current state ${fireplaceDevice.currentValue("switch")} changing to ${value}"
				fireplaceDevice.sendEvent(name: "switch", value: value)
			}
            if (value == "off")
            {
                def fanDevice = fireplaceDevice.getChildDevice("bond:" + fireplace + ":fan")
                if (fanDevice)
                    fanDevice.sendEvent(name: "speed", value: "off")
            }
			break;
		}
	}
}

def updateDevices() {
    for (fan in fans) {
        def state = getState(fan)
        def device = getChildDevice("bond:" + fan)
        def deviceLight = device.getChildDevice("bond:" + fan + ":light")
		def deviceUpLight = device.getChildDevice("bond:" + fan + ":uplight")
		def deviceDownLight = device.getChildDevice("bond:" + fan + ":downlight")
        if (state.power > 0)
        {
            device.sendEvent(name: "switch", value: "on")
			device.sendEvent(name: "speed", value: translateBondFanSpeedToHE(state.fanProperties?.getAt(fan)?.max_speed ?: 3, state.speed))
        }
        else
        {
            device.sendEvent(name: "switch", value: "off")
			device.sendEvent(name: "speed", value: "off")
        }
        if (deviceLight)
        {
			if (state.brightness != null)
			{
				if (state.light == 0)
					deviceLight.sendEvent(name: "level", value: 0)
				else
					deviceLight.sendEvent(name: "level", value: state.brightness)
			}
			else
			{
				if (state.light > 0)
					deviceLight.sendEvent(name: "switch", value: "on")
				else
					deviceLight.sendEvent(name: "switch", value: "off")
			}
        }
		if (deviceUpLight)
		{
			if (state.up_light_brightness != null)
			{
				if (state.up_light == 0)
					deviceUpLight.sendEvent(name: "level", value: 0)
				else
					deviceUpLight.sendEvent(name: "level", value: state.up_light_brightness)
			}
			else
			{
				if (state.light > 0 && state.up_light > 0)
					deviceUpLight.sendEvent(name: "switch", value: "on")
				else
					deviceUpLight.sendEvent(name: "switch", value: "off")
			}
		}
		if (deviceDownLight)
		{
			if (state.down_light_brightness != null)
			{
				if (state.down_light == 0)
					deviceDownLight.sendEvent(name: "level", value: 0)
				else
					deviceDownLight.sendEvent(name: "level", value: state.down_light_brightness)
			}
			else
			{
				if (state.light > 0 && state.down_light > 0)
					deviceDownLight.sendEvent(name: "switch", value: "on")
				else
					deviceDownLight.sendEvent(name: "switch", value: "off")	
			}				
		}
		if (device.hasAttribute("direction"))
		{
			if (state.direction == 1)
				device.sendEvent(name: "direction", value: "forward")
			else if (state.direction == -1)
				device.sendEvent(name: "direction", value: "reverse")
		}
    }
    
	if (fireplaces != null)
	{
		for (def i = 0; i < fireplaces.size(); i++)
		{
			def state = getState(fireplaces[i])
			def device = getChildDevice("bond:" + fireplaces[i])
			def deviceFan = device.getChildDevice("bond:" + fireplaces[i] + ":fan")
			def deviceLight = device.getChildDevice("bond:" + fireplaces[i] + ":light")
			
			if (state.power > 0)
			{
				if (this.getProperty("fireplaceSensor${i}") == null)
				{
					device.sendEvent(name: "switch", value: "on")
				}
				if (deviceFan)
				{
					deviceFan.sendEvent(name: "speed", value: translateBondFanSpeedToHE(state.fireplaceProperties?.getAt(fireplaces[i])?.max_speed ?: 3, state.fpfan_speed))
				}
				
				if (deviceLight)
				{
					if (state.light == 1)
						deviceLight.sendEvent(name: "switch", value: "on")
					else
						deviceLight.sendEvent(name: "switch", value: "off")
				}
			}
			else 
			{
				if (this.getProperty("fireplaceSensor${i}") == null)
				{
					device.sendEvent(name: "switch", value: "off")
				}
				if (deviceFan)
				{
					deviceFan.sendEvent(name: "speed", value: "off")
				}
				if (deviceLight)
				{
					deviceLight.sendEvent(name: "switch", value: "off")
				}
			}
			
		}
	}
	
	if (shades != null)
	{
		for (shade in shades)
		{
			def state = getState(shade)
			def device = getChildDevice("bond:" + shade)
			
			if (state.open == 1)
			{
				device.sendEvent(name: "windowShade", value: "open")
			}
			else
			{
				device.sendEvent(name: "windowShade", value: "closed")
			}
		}
	}
}

def handleOn(device, bondId) {
	logDebug "Handling On event for ${bondId}"
	if (hasAction(bondId, "TurnOn")) 
	{
		if (executeAction(bondId, "TurnOn") && shouldSendEvent(bondId))
		{
			device.sendEvent(name: "switch", value: "on")
		}
	}
}

def handleLightOn(device, bondId) {
    logDebug "Handling Light On event for ${bondId}"
	if (device.deviceNetworkId.contains("uplight") && hasAction(bondId, "TurnUpLightOn"))
	{
	        if (executeAction(bondId, "TurnUpLightOn")) 
		{
			device.sendEvent(name: "switch", value: "on")
		}
	}
	else if (device.deviceNetworkId.contains("downlight") && hasAction(bondId, "TurnDownLightOn"))
	{
	        if (executeAction(bondId, "TurnDownLightOn")) 
		{
			device.sendEvent(name: "switch", value: "on")
		}
	}
    else if (hasAction(bondId, "TurnLightOn")) 
	{
        if (executeAction(bondId, "TurnLightOn")) 
		{
			device.sendEvent(name: "switch", value: "on")
		}
    }
}

def handleLightOff(device, bondId) {
    logDebug "Handling Light Off event for ${bondId}"   
	if (device.deviceNetworkId.contains("uplight") && hasAction(bondId, "TurnUpLightOff"))
	{
		if (executeAction(bondId, "TurnUpLightOff")) 
		{
			device.sendEvent(name: "switch", value: "off")
		}
	}
	else if (device.deviceNetworkId.contains("downlight") && hasAction(bondId, "TurnDownLightOff"))
	{
		if (executeAction(bondId, "TurnDownLightOff")) 
		{
			device.sendEvent(name: "switch", value: "off")
		}
	}	
    else if (hasAction(bondId, "TurnLightOff")) 
	{
        if (executeAction(bondId, "TurnLightOff")) 
		{
			device.sendEvent(name: "switch", value: "off")
		}
    }
}

def handleLightLevel(device, bondId, level) {
	logDebug "Handling Light Level event for ${bondId}"
	if (device.deviceNetworkId.contains("uplight") && hasAction(bondId, "SetUpLightBrightness"))
	{
		if (executeAction(bondId, "SetUpLightBrightness", level)) 
		{
			device.sendEvent(name: "level", value: level)
		}
	}
	else if (device.deviceNetworkId.contains("downlight") && hasAction(bondId, "SetDownLightBrightness"))
	{
		if (executeAction(bondId, "SetDownLightBrightness", level)) 
		{
			device.sendEvent(name: "level", value: level)
		}
	}
    else if (hasAction(bondId, "SetBrightness")) 
	{
        if (executeAction(bondId, "SetBrightness", level)) 
		{
			device.sendEvent(name: "level", value: level)
		}
    }
}

def handleOpen(device, bondId)
{
	logDebug "Handling Open event for ${bondId}"
	
	if (hasAction(bondId, "Open")) 
	{
        if (executeAction(bondId, "Open")) 
		{
			device.sendEvent(name: "windowShade", value: "open")
		}
    }
}

def handleClose(device, bondId)
{
	logDebug "Handling Close event for ${bondId}"
	
	if (hasAction(bondId, "Close")) 
	{
        if (executeAction(bondId, "Close")) 
		{
			device.sendEvent(name: "windowShade", value: "closed")
		}
    }
}

def translateBondFanSpeedToHE(max_speeds, speed)
{
	def speedTranslations = 
	[
		10: [10: "high", 9: "high", 8: "medium-high", 7: "medium-high", 6: "medium", 5: "medium", 4: "medium-low", 3: "medium-low", 2: 1, 1: "low"],
		9: [9: "high", 8: "medium-high", 7: "medium-high", 6: "medium", 5: "medium", 4: "medium-low", 3: "medium-low", 2: "low", 1: "low"],
		8: [8: "high", 7: "medium-high", 6: "medium-high", 5: "medium", 4: "medium", 3: "medium-low", 2: "medium-low", 1: "low"],
		7: [7: "high", 6: "medium-high", 5: "medium", 4: "medium", 3: "medium-low", 2: "medium-low", 1: "low"],
		6: [6: "high", 5: "medium-high", 4: "medium", 3: "medium", 2: "medium-low", 1: "low"],
		5: [5: "high", 4: "medium-high", 3: "medium", 2: "medium-low", 1: "low"],
		4: [4: "high", 3: "medium", 2: "medium-low", 1: "low"],
		3: [3: "high", 2: "medium", 1: "low"],
		2: [2: "high", 1: "low" ]
	]
	logDebug "Translating ${speed} to BOND"
	if (!speed.toString().isNumber())
		return speed
		
	if (max_speeds > 10 || speed > max_speeds)
		return 0
		
	return speedTranslations[max_speeds][speed]
}

def translateHEFanSpeedToBond(max_speeds, speed)
{
	if (speed.isNumber())
		return speed.toInteger()
		
		
	def speedTranslations =
	[
		10: ["high": 10, "medium-high": 8, "medium": 5, "medium-low": 3, "low": 1],
		9: ["high": 9, "medium-high": 7, ":medium": 5, "medium-low": 3, "low": 1],
		8: ["high": 8, "medium-high": 6, "medium": 4, "medium-low": 3, "low": 1],
		7: ["high": 7, "medium-high": 6, "medium": 4, "medium-low": 3, "low": 1 ],
		6: ["high": 6, "medium-high": 5, "medium": 3, "medium-low": 2, "low": 1],
		5: ["high": 5, "medium-high": 4, "medium": 3, "medium-low": 2, "low": 1],
		4: ["high": 4, "medium": 3, "medium-low": 2, "low": 1],
		3: ["high": 3, "medium": 2, "low": 1],
		2: ["high": 2, "low": 1]
	]
	
	if (max_speeds > 10)
		return null
		
	return speedTranslations[max_speeds][speed]
}

def handleFanSpeed(device, bondId, speed) {
    logDebug "Handling Fan Speed event for ${bondId}"

	if (speed == "off")
	{
		if (handleOff(device, bondId))
			device.sendEvent(name: "speed", value: "off")
	}	
	else if (speed == "on")
		handleOn(device, bondId)
    else if (hasAction(bondId, "SetSpeed")) 
	{
        if (executeAction(bondId, "SetSpeed", translateHEFanSpeedToBond(state.fanProperties?.getAt(bondId)?.max_speed ?: 3, speed))) 
		{
			device.sendEvent(name: "switch", value: "on")
			device.sendEvent(name: "speed", value: speed)
		}
    }
}

def handleFPFanSpeed(device, bondId, speed) {
    logDebug "Handling Fireplace Fan Speed event for ${bondId}"

	if (speed == "off")	
		handleFPFanOff(device, bondId)
	else if (speed == "on")
		handleFPFanOn(device, bondId)
    else if (hasAction(bondId, "SetSpeed")) 
	{
        if (executeAction(bondId, "SetSpeed", translateHEFanSpeedToBond(state.fireplaceProperties?.getAt(bondId)?.max_speed ?: 3, speed))) 
		{
			device.sendEvent(name: "speed", value: speed)
		}
    }
}

def handleFPFanOn(device, bondId) {
	logDebug "Handling Fan On event for ${bondId}"
	
	if (hasAction(bondId, "TurnFpFanOn")) 
	{
		if (executeAction(bondId, "TurnFpFanOn")) 
		{
			device.sendEvent(name: "switch", value: "on")
			device.sendEvent(name: "speed", value: "on")
			return true
		}
	}
	return false
}

def handleFPFanOff(device, bondId) {
	logDebug "Handling Fan Off event for ${bondId}"
	
	if (hasAction(bondId, "TurnFpFanOff")) 
	{
		if (executeAction(bondId, "TurnFpFanOff")) 
		{
			device.sendEvent(name: "switch", value: "off")
			device.sendEvent(name: "speed", value: "off")
			return true
		}
	}
	return false
}

def handleOff(device, bondId) {
	logDebug "Handling Off event for ${bondId}"

	if (hasAction(bondId, "TurnOff")) 
	{
		if (executeAction(bondId, "TurnOff") && shouldSendEvent(bondId)) 
		{
			device.sendEvent(name: "switch", value: "off")
			if (device.hasCapability("FanControl"))
				device.sendEvent(name: "speed", value: "off")
			return true
		}
	}
	return false
}

def handleDirection(device, bondId, direction)
{
	logDebug "Handling Direction event for ${bondId}"
	if (hasAction(bondId, "SetDirection")) 
	{
		def bondDirection = 1
		if (direction == "reverse")
			bondDirection = -1
        if (executeAction(bondId, "SetDirection", bondDirection)) 
		{
			device.sendEvent(name: "direction", value: direction)
		}
    }
}

def getState(bondId) {
	def params = [
		uri: "http://${hubIp}",
		path: "/v2/devices/${bondId}/state",
		contentType: "application/json",
		headers: [ 'BOND-Token': hubToken ]
	]
	def stateToReturn = null
	try
	{
		httpGet(params) { resp ->
			if (checkHttpResponse("getState", resp))
				stateToReturn = resp.data
		}
	}
	catch (e)
	{
		log.debug "HTTP Exception Received on GET: $e"
	}
	return stateToReturn
}

def hasAction(bondId, commandType) {
	logDebug "searching for ${commandType} for ${bondId}"
	def params = [
		uri: "http://${hubIp}",
		path: "/v2/devices/${bondId}/actions",
		contentType: "application/json",
		headers: [ 'BOND-Token': hubToken ]
	]
	def commandToReturn = false
	try
	{
		httpGet(params) { resp ->
			if (checkHttpResponse("hasAction", resp))
			{
				for (commandId in resp.data) {
					if (commandId.key == "_")
						continue
					if (commandId.key == commandType) {
						logDebug "found command ${commandId.key} for ${bondId}"
						commandToReturn = true
						break
					}
				}
			}
		}
	}
	catch (e)
	{
		log.debug "HTTP Exception Received on GET: $e"
	}
	return commandToReturn
}

def executeAction(bondId, action) {
	def params = [
		uri: "http://${hubIp}",
		path: "/v2/devices/${bondId}/actions/${action}",
		contentType: "application/json",
		headers: [ 'BOND-Token': hubToken ],
		body: "{}"
	]
	def isSuccessful = false
	logDebug "calling action ${action}"
	try
	{
		httpPut(params) { resp ->
			isSuccessful = checkHttpResponse("executeAction", resp)
		}
	}
	catch (e) 
	{
		log.debug "HTTP Exception Received on PUT: $e"
	}
	return isSuccessful
}

def executeAction(bondId, action, argument) {
	def params = [
		uri: "http://${hubIp}",
		path: "/v2/devices/${bondId}/actions/${action}",
		contentType: "application/json",
		headers: [ 'BOND-Token': hubToken ],
		body: '{"argument": ' + argument +'}'
	]
	def isSuccessful = false
	logDebug "calling action ${action} ${params.body}"
	try
	{
		httpPut(params) { resp ->
			isSuccessful = checkHttpResponse("executeAction", resp)
		}
	}
	catch (e) 
	{
		log.debug "HTTP Exception Received on PUT: $e"
	}
	return isSuccessful
}

def shouldSendEvent(bondId) {
	for (fan in fans) 
	{
		if (fan == bondId)
			return true;
	}
	
	if (fireplaces != null)
	{
		for (def i = 0; i < fireplaces.size(); i++)
		{
			if (fireplaces[i] == bondId)
			{
				if (this.getProperty("fireplaceSensor${i}") != null)
					return false;
				return true;
			}
		}
	}
	return true;
}

def logDebug(msg) {
    if (settings?.debugOutput) {
		log.debug msg
	}
}

def checkHttpResponse(action, resp) {
	if (resp.status == 200 || resp.status == 201 || resp.status == 204)
		return true
	else if (resp.status == 400 || resp.status == 401 || resp.status == 404 || resp.status == 409 || resp.status == 500)
	{
		log.error "${action}: ${resp.data.error_msg} (id: ${resp.data.error_id}, code: ${resp.data.error_code})"
		return false
	}
	else
	{
		log.error "${action}: unexpected HTTP response: ${resp.status}"
		return false
	}
}