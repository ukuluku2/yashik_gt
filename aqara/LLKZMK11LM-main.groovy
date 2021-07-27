/*
 * Smartthings DH for 
 * Aqara Two-way Control Module Zigbee Relay Controller . Product: LLKZMK11LM
 * 
 * This handler is a refactored version of DH for for Aqara Switch QBKG12LM
 * https://community.smartthings.com/t/device-handler-for-aqara-wired-wall-switch/101062/188
 * use information from Smartthings ZigBee Switch & Diego Schicoh & Zooz Power Strip Outlet & Koenkk zigbee2mqtt & aonghusmor & veeceeoh
 *
 * Resources:
 *
 * https://github.com/orangebucket/Anidea-for-SmartThings/blob/master/devicetypes/orangebucket/anidea-for-aqara-temperature.src/anidea-for-aqara-temperature.groovy
 * https://github.com/SmartThingsCommunity/SmartThingsPublic/blob/master/devicetypes/smartthings/zigbee-metering-plug-power-consumption-report.src/zigbee-metering-plug-power-consumption-report.groovy
 * https://github.com/guyeeba/Hubitat/blob/master/Drivers/Aqara%20QBKG03LM-QBKG04LM.groovy
 * https://github.com/dresden-elektronik/deconz-rest-plugin/issues/2909
 * https://community.smartthings.com/t/device-handler-for-aqara-wired-wall-switch/101062/182
 */

/* 

  For device with FW 0x24
	zbjoin: {"dni":"64AB","d":"00158D0006D4F038","capabilities":"8E",
	   "endpoints":[{"simple":"01 0104 0101 02 0B 0000 0003 0004 0005 0001 0002 000A 0006 0010 0B04 000C 02 0019 000A","application":"24","manufacturer":"LUMI","model":"lumi.relay.c2acn01"},
	                {"simple":"02 0104 0101 02 04 0006 0010 0004 0005 00","application":"","manufacturer":"","model":""}],"parent":"0000","joinType":1,"joinDurationMs":689,"joinAttempts":1}

Cluster 0x0000 (Basic)
======================
Attr    Name
0x0000	ZCLVersion
0x0001  ApplicationVersion
0x0002  StackVersion
0x0003  HWVersion
0x0006  DateCode 

Catchall, Attribute 0xFF01

|Pos   |Attribute | Data Type | Len |Description                    |
|------|----------|-----------|-----|-------------------------------|
|0-3   |          |           |     |                               |     	           	     	             
|4-6   |0x03      | 0x28      |	1   | Temperature                   |
|7-10  | 0x05     | 0x21      |	2   |	                            |
|11-20 | 0x07     | 0x27      |	8   |	                            |
|21-24 | 0x08     | 0x21      |	2   | Always [24, 12] - 4644        |
|25-28 | 0x09     | 0x21      |	2   |	                            |
|29-31 | 0x64     | 0x10      |	1   | switch 1 status 00-off, 01-on |
|32-34 | 0x65     | 0x10      |	1   | switch 2 status               |
|35-37 | 0x6E     | 0x20      |	1   |	                            |
|38-40 | 0x6F     | 0x20      |	1   |	                            |
|41-43 | 0x94     | 0x20      |	1   |	                            |
|44-49 | 0x95     | 0x39      |	4   | energy                        |
|50-55 | 0x96     | 0x39      |	4   | voltage multiplied by 10      |
|56-61 | 0x97     | 0x39      |	4   | current                       |
|62-67 | 0x98     | 0x39      |	4   | power                         |
|68-71 | 0x9B     | 0x21      |	2   |	                            |



Cluster 0x0001 (Power Configuration)
====================================
0x0001 MainsFrequency uint8 * 2


Cluster 0x0002 (Device Temperature Configuration)
=================================================
Cluster 0x0003 (Identify)
=========================

Cluster 0x0004 (Groups)
=======================

Cluster 0x0005 (Scenes)
=======================

Cluster 0x0006 (On/Off)
=======================
0x0000 OnOff
0x4000 GlobalScene-Control
0x4001 OnTime
0x4002 OffWaitTime


Cluster 0x000a (Time)
=======================
0x0000 Time
0x0001 Time Status


Cluster 0x000C  Analog Input (Basic)
=====================================
0x0055 Power 

Cluster 0x0010 (Binary Output (Basic))
======================================
0xFF06 Interlock mode

Cluster 0x0B04 (Electrical Measurement Cluster)
===============================================

*/

metadata {
	definition (name: "Aqara LLKZMK11LM 2 Channel Zigbee Relay Main", namespace: "Leza", author: "Yashik", vid:"generic-switch-power-energy") {
		capability "Actuator"
		capability "Switch"
		capability "Temperature Measurement"
		capability "Voltage Measurement"
		capability "Power Meter"
		capability "Energy Meter"
		capability "Sensor"
		capability "Configuration"
		capability "Refresh"
		capability "Health Check"
		
		(1..2).each {
			attribute "sw${it}Switch", "string"
			attribute "sw${it}Name", "string"
			command "sw${it}On"
			command "sw${it}Off"
		}
		attribute "current", "number"
		attribute "interlock", "string"
		
		attribute "Info", "string"
		
		

		fingerprint	profileId: "0104", deviceId: "0101", 
					inClusters: "0000 0003 0004 0005 0001 0002 000A 0006 0010 0B04 000C",
					outClusters: "0019,000A",
					manufacturer: "LUMI", model: "lumi.relay.c2acn01", deviceJoinName: "Aqara Dual Switch LLKZMK11LM"
	}	
	
	preferences {
		getBoolInput("debugOutput", "Enable Debug Logging", true)
		input name: "tempUnitDisplayed", type: "enum", title: "Displayed Temperature Unit", description: "", defaultValue: "1", required: true, multiple: false, options:[["1":"C (Celsius)"], ["2":"F (Fahrenheit)"]]
		input name: "temperatureOffset", type: "decimal", title: "Temperature offset", defaultValue: 0, range: "-15..10", description: "Temperature calibration value", required: true, displayDuringSetup:false
		input name: "interlock", type: "bool", title: "Interlock", description: "Switches off relay if another is switched on", defaultValue: false, required: true
	
		input name: "refreshActions", type: "bool", title: "Refresh info", description: "Sends queries to the device", defaultValue: false, required: false, displayDuringSetup:false
	}

	tiles(scale: 2) {
		multiAttributeTile(name:"switch", type: "lighting", width: 6, height: 4, canChangeIcon: true){
			tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
				attributeState "on", label:'${name}', action:"switch.off", icon:"st.switches.light.on", backgroundColor:"#00A0DC", nextState:"turningOff"
				attributeState "off", label:'${name}', action:"switch.on", icon:"st.switches.light.off", backgroundColor:"#ffffff", nextState:"turningOn"
				attributeState "turningOn", label:'${name}', action:"switch.off", icon:"st.switches.light.on", backgroundColor:"#00A0DC", nextState:"turningOff"
				attributeState "turningOff", label:'${name}', action:"switch.on", icon:"st.switches.light.off", backgroundColor:"#ffffff", nextState:"turningOn"
			}
			tileAttribute("device.lastCheckin", key: "SECONDARY_CONTROL") {
				attributeState("default", label:'Last Update: ${currentValue}')
			}
		}
		
		valueTile("sw1Name", "device.sw1Name", decoration:"flat", width:5, height: 1) {
			state "default", label:'${currentValue}'
		}
		standardTile("sw1Switch", "device.sw1Switch", width:1, height: 1) {
			state "on", label:'ON', action:"sw1Off", backgroundColor: "#00A0DC"
			state "off", label:'OFF', action:"sw1On", backgroundColor:"#ffffff"
		}
		valueTile("sw2Name", "device.sw2Name", decoration:"flat", width:5, height: 1) {
			state "default", label:'${currentValue}'
		}
		standardTile("sw2Switch", "device.sw2Switch", width:1, height: 1) {
			state "on", label:'ON', action:"sw2Off", backgroundColor: "#00A0DC"
			state "off", label:'OFF', action:"sw2On", backgroundColor:"#ffffff"
		}

		standardTile("refresh", "device.refresh", width: 2, height: 2) {
			state "default", label:'Refresh', action: "refresh", icon:"st.secondary.refresh-icon"
		}		
		
		valueTile("power", "device.power", width: 3, height: 2) {
			state "power", label:'${currentValue} W', icon:"st.Appliances.appliances5"
		}
		valueTile("energy", "device.energy", width: 3, height: 2) {
			state "energy", label:'${currentValue} kWh', icon:"st.Health & Wellness.health7"
		}
		
		valueTile("voltage", "device.voltage", width: 2, height: 2) {
			state "voltage", label:'${currentValue} V', icon:"st.Appliances.appliances17"
		}

		valueTile("temperature", "device.temperature", width: 2, height: 2) {
			state "temperature", label:'${currentValue}', unit:'C', icon:"st.Weather.weather2"
		}
				
		
		main (["switch"])
		details (detailsTiles)
	}
}

private getDetailsTiles() {	
	def tiles = ["switch"]
	(1..2).each {
		tiles << "sw${it}Name"
		tiles << "sw${it}Switch"
	}
    tiles << "refresh" << "power" << "energy" << "voltage" << "temperature" 
	
	return tiles
}

private getBoolInput(name, title, defaultVal) {
	input "${name}", "bool", 
	title: "${title}?", 
	defaultValue: "${defaultVal}", 
	required: false
}


private Key_SWITCH_1()		{ 0x64 }
private Key_SWITCH_2()		{ 0x65 }
// kW/h
private Key_ENERGY_METER()	{ 0x95 }
// Wats
private Key_POWER_METER()	{ 0x98 } 


/*
 * DH API implementation
 */
def installed() { 
	logDebug "installed()... device=${device} settings=${settings}"
	
	state.children          = [ "off", "off" ]
	state.info = [ : ]
	state.temperature = 0
	state.current = 0
	state.interlock = 3
	sendEvent( name: 'power',  		value: 0.0, unit: 'W',   displayed: true )
	sendEvent( name: 'energy', 		value: 0.0, unit: 'kWh', displayed: true )
	sendEvent( name: 'voltage',		value: 0,   unit: 'V',   displayed: true )
	sendEvent( name: 'current',		value: 0,   unit: 'A',   displayed: true )
	sendEvent( name: 'temperature', value: 0,   unit: getTempUnits(),   displayed: true )
	sendEvent( name: 'interlock', value: getInterlockState(),  displayed: true )

	createChildDevices()
}

def configure() {
	logDebug "configure()... settings=${settings} childDevices=${childDevices}"
	
	state.info = [ : ]
	state.interlock = 3
	state.temperature = 0
	sendEvent( name: 'interlock', value: getInterlockState(),  displayed: true )
	updateHealthCheckInterval()

	
	/*
	 * Cluster ID 0x0b04 Electrical Measurement Cluster 
	 *     0x0000 Basic Information
	 *     0x0005 AC (Single Phase or Phase A) Measurements
	 */
	 
	def cmds = []
	cmds += 	
			zigbee.configureReporting(0x000C, 0x0055, 0x39, 1, 10, 1, [destEndpoint: 1, mfgCode: 0x115F]) +
			zigbee.configureReporting(0x0B04, 0x050B, 0x29, 1, 10, 1, [destEndpoint: 1, mfgCode: 0x115F]) +
			zigbee.readAttribute(0x0000, 0x0000, [destEndpoint: 0x01]) + // ZCLVersion
			zigbee.readAttribute(0x0000, 0x0001, [destEndpoint: 0x01]) + // ApplicationVersion
			zigbee.readAttribute(0x0000, 0x0002, [destEndpoint: 0x01]) + // StackVersion
			zigbee.readAttribute(0x0000, 0x0003, [destEndpoint: 0x01]) + // HWVersion
			zigbee.readAttribute(0x0000, 0x0006, [destEndpoint: 0x01]) + // DateCode
//			zigbee.readAttribute(0x000C, 0x0055, [destEndpoint: 0x01]) + // Power meter - will be called by refresh
//			zigbee.readAttribute(0x0010, 0xFF06, [destEndpoint: 0x01, mfgCode: 0x115F]) + will be called by refresh
			zigbee.readAttribute(0x0B04, 0x0000, [destEndpoint: 0x01]) 
			
	
	def actions = []
	
    cmds?.each {
		actions << new physicalgraph.device.HubAction(it)
	}
	sendHubCommand(actions,250)
	
	runIn(30, refresh)
}

def updated() {	
	logDebug "updated()... device=${device} settings=${settings} temp=" + getTempUnits()

	try {

		if (settings?.refreshActions == true)
		{
			device.updateSetting('refreshActions', false)
			refreshAction()
			return
		}

		if ( state.temperature != null ) {
			handleSetTemperatureEvent(state?.temperature)
		}

		def cmds = []

		if ( getInterlockConfig() != state.interlock )
		{
			logDebug "updated: changing interlock state to " + getInterlockConfig() + " hex=" + zigbee.convertToHexString(getInterlockConfig(),2)
	
			cmds += zigbee.writeAttribute(0x0010, 0xFF06, 0x10, zigbee.convertToHexString(getInterlockConfig(),2), [mfgCode: 0x115F] )
		}

		if (!isDuplicateCommand(state.lastUpdated, 3000)) {
			state.lastUpdated = new Date().time
			
			if (childDevices?.size() != 2)	createChildDevices()
		}

		cmds += refresh()
		
		def actions = []
		cmds?.each {
			actions << new physicalgraph.device.HubAction(it)
		}

		sendHubCommand(actions,250)
	}
	catch (ex) {
		logError "updated: Exeption: $ex  "
	}
}

def ping() {
	logDebug "ping()..."
	//parse("catchall: 0104 0000 01 01 0000 00 64AB 00 01 115F 0A 01 01FF4244032820052120000727000000000000000008212412092100016410006510006E20006F20009420059539CDCCCC3A9639CE4012459739F029A43B9839602E8F3D9B210000")
	return zigbee.onOffRefresh() + zigbee.onOffConfig()
}


// Parse incoming device messages to generate events
def parse(String description) {

	def result = []
	try {
		def descMap = zigbee.parseDescriptionAsMap(description)
		logInfo "parse(): description is '$description'  descMap is $descMap"
		
		def command  = descMap?.command

		if (description?.startsWith("read attr -")) {
			def endpoint = descMap?.endpoint as int
			switch (descMap.clusterInt)
			{
				case 0x0000:
					switch (descMap?.attrInt)
					{
						case 0x0000:
							state.info.ZCLVersion = "0x"+descMap.value
						break
						case 0x0001:
							state.info.ApplicationVersion = "0x"+descMap.value
						break
						case 0x0002:
							state.info.StackVersion = "0x"+descMap.value
						break
						case 0x0003:
							state.info.HWVersion = "0x"+descMap.value
						break
						case 0x0006:
							state.info.DateCode = hexStringToString(descMap.value)
						break
						default:
						    state.info["Cluster 0x0000 " + descMap?.attrInt] = descMap.value
					}
				break
				
				case zigbee.POWER_CONFIGURATION_CLUSTER: // 0x0001
					if ( descMap?.attrInt == 0x0000 ) { // Mains information
						def value = (int) (Integer.parseInt(descMap.value, 16) / 10)
						logDebug "parse: Mains information: voltage: endpoint=${endpoint} ${descMap.value}  value=${value}V"
					}
					state.info["Cluster 0x0001 " + descMap?.attrInt] = descMap.value
				break
				
				case 0x0002: //Temperature 
					if ( descMap?.attrInt == 0x0000) {
						def value = Integer.parseInt(descMap.value, 16)
						if (value > 127)	value = value - 256
						logDebug "parse: Temperature: endpoint=${endpoint} ${descMap.value}  value=${value}C"

						handleSetTemperatureEvent(value)
					} else {
						state.info["Cluster 0x0002 " + descMap?.attrInt] = descMap.value
					}
					
				break

				case zigbee.ONOFF_CLUSTER: // 0x0006
					logDebug "parse: ON/OFF endpoint=${endpoint} value=${descMap.value}"
					handleSwitchClusterEvent(endpoint, descMap?.attrInt, command, descMap.value)
				break

				case 0x000C: // Analog input (consumption) Power meter
					switch (descMap?.attrInt)
					{
						case 0x0041:
							def value = Float.intBitsToFloat(Long.parseLong(descMap.value, 16).intValue())
							logDebug "parse: Analog input: max present value: endpoint=${endpoint} ${descMap.value}  value=${value}W"
							state.info.MaxPresentValue = value
						break
					    case 0x0055: 
							// 0x0055 Present value, 0x001C Description, 0x0041 MaxPresentValue, 
							// 0x0045 MinPresentValue, 0x0051 OutOfService, 0x006A Resolution 
							def value = Float.intBitsToFloat(Long.parseLong(descMap.value, 16).intValue())
							logDebug "parse: Analog input: power: endpoint=${endpoint} ${descMap.value}  value=${value}W"
							sendEvent(name: "power", value: value, unit: 'W', displayed: true, isStateChange: true, descriptionText: "Cluster 0x000C, Attr: 0x0055")						
						break

						case 0x001C:
							state.info.AnalogInputDescription = hexStringToString(descMap.value)
						break

						default:
							logInfo "Analog input: Unknown attribute=${descMap?.attrInt}   ${descMap.value}"
							state.info["Cluster 0x000C " + descMap?.attrInt] = descMap.value
					}
				break // 0x000C Analog input cluster
				
				case 0x0010: // Binary output
					if (descMap?.attrInt == 0xFF06) {
						state.interlock = Integer.parseInt(descMap?.value, 16)
						sendEvent( name: 'interlock', value: getInterlockState(),  displayed: true )
					}
				break
				
				
				case 0x0B04: // Electrical Measurement Cluster
					switch (descMap?.attrInt)
					{
						case 0x0000: // Basic Information
							state.info.MeasurementType = descMap.value
						break
						default:
							state.info["Cluster 0x0B04 " + descMap?.attrInt] = descMap.value
					}
					
				break
				default:
					state.info["Cluster " + descMap?.clusterId + "  " + descMap?.attrInt] = descMap.value
					logInfo "Unknown cluster ID:  ${descMap.clusterInt}"
			}
			String info = state.info
			sendEvent(name: "Info", value: info)
		} else if (description?.startsWith("catchall:")) {
			def endpoint = descMap.sourceEndpoint as int
			switch (descMap.clusterInt)
			{
				case zigbee.BASIC_CLUSTER: // 0x0000
					handleBasicClusterEvent(endpoint, descMap.attrInt, command, descMap)
				break

				case zigbee.ONOFF_CLUSTER: // 0x0006
					if ( descMap?.commandInt == 1 )
					{
						// This is the response to 
						// readAttribute(zigbee.ONOFF_CLUSTER, 0x0000)
						// Data contains 5 bytes: 2bytes attribute, 1b result, 1 bytes encoding, 1 byte value
						handleSwitchClusterEvent(endpoint, 0x0000, command, descMap.value)
					} else if ( descMapcommandInt == 0xB ) {
						// Data contains 2 bytes, the first one contains the status of EP
						handleSwitchClusterEvent(endpoint, 0x0000, command, descMap.data[0])
					}
					// descMap.attrInt is not set here, use zero
					/*
					descMap.additionalAttrs?.each {	
						//zigbee.convertHexToInt(descMap.value)
						handleSwitchClusterEvent ( endpoint, it?.attrInt, command, it.value)
					}
					*/								
				break
				
				case 0x000C:
				// Looks like Power reporting. No attribute is set in descMap
				
				break
				
				case 0x0010:
					log.info "Cluster 0x0010 Binary output " + descMap.data + " interlock modification completion"
					// It seems like catchall provides the status of the previous write (set) operation, instead of the new value
					// So, we just issuing the read request to retrieve the new value
					//		state.interlock = zigbee.convertHexToInt( descMap.data[0] )
					//		sendEvent( name: 'interlock', value: getInterlockState(),  displayed: true )
					sendHubCommand(zigbee.readAttribute(0x0010, 0xFF06, [destEndpoint: 0x01, mfgCode: 0x115F]).collect { new physicalgraph.device.HubAction(it) }, 250)
				break
				
				case 0x0B04:
					switch (descMap?.attrInt)
					{
						case 0x0000:
						break
						
						case 0x0003:
						break
					}
				break
			}
			
		}
		else {
			logInfo "--unknown event()"
		}
		
	}
	catch (ex) {
		logError "parse: Exeption: $ex  "
	}

	def now = new Date().format("dd-MM-yyyy HH:mm:ss", location.timeZone)
	sendEvent(name: "lastCheckin", value: now, displayed: false)
	executeSendEvent(findChildByEndPoint(1), createEventMap("lastCheckin", now))
	executeSendEvent(findChildByEndPoint(2), createEventMap("lastCheckin", now))
	
	//logDebug "parse() completed: " + result.dump()
	return result
}


/* 
 * Actions 
 */
 
def on() {
	logDebug "on()..."
	sw1On()
	sw2On()
}

def off() {
	logDebug "off()..."
	sw1Off()
	sw2Off()
}

def sw1On()  { childOn(getChildDeviceNetworkId(1)) }
def sw2On()  { childOn(getChildDeviceNetworkId(2)) }
def sw1Off() { childOff(getChildDeviceNetworkId(1)) }
def sw2Off() { childOff(getChildDeviceNetworkId(2)) }

def refresh() {
	logDebug "refresh()..."	
	// TODO test it
	def Cmds = 
                zigbee.readAttribute(0x0002, 0x0000, [destEndpoint: 0x01]) +		            // temperature
                zigbee.readAttribute(zigbee.ONOFF_CLUSTER, 0x0000, [destEndpoint: 0x01]) +		// switch 1 state
                zigbee.readAttribute(zigbee.ONOFF_CLUSTER, 0x0000, [destEndpoint: 0x02]) +		// switch 2 state
                zigbee.readAttribute(0x000C, 0x0055, [destEndpoint: 0x01]) +                     // Analog input (consumption) Power meter 
                zigbee.readAttribute(0x0010, 0xFF06, [destEndpoint: 0x01, mfgCode: 0x115F])     // interlock
 	return Cmds
}


def refreshAction() {

	logDebug "refreshActions()..."	
	
	def cmds = refresh()
		
	def actions = []
	cmds?.each {
		actions << new physicalgraph.device.HubAction(it)
	}

	sendHubCommand(actions,250)
}

// This is used for tests
def refreshAction2() {
	logDebug "refreshActions()..."	
	def cmds = 
				zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0xFF01, [destEndpoint: 0x01, mfgCode: 0x115F]) +		
				zigbee.readAttribute(0x0001, 0x0000, [destEndpoint: 0x01]) +		// voltage
				zigbee.readAttribute(0x0001, 0x0000, [destEndpoint: 0x02]) +
//			   	zigbee.readAttribute(0x0001, 0x0000, [destEndpoint: 0x01, mfgCode: 0x115F]) +
//			   	zigbee.readAttribute(0x0001, 0x0000, [destEndpoint: 0x02, mfgCode: 0x115F]) +
//				zigbee.readAttribute(0x0001, 0x0001, [destEndpoint: 0x01]) +		// mains frequency
//				zigbee.readAttribute(0x0001, 0x0001, [destEndpoint: 0x02]) +
//			   	zigbee.readAttribute(0x0001, 0x0001, [destEndpoint: 0x01, mfgCode: 0x115F]) +
//			   	zigbee.readAttribute(0x0001, 0x0001, [destEndpoint: 0x02, mfgCode: 0x115F])
				zigbee.readAttribute(0x0002, 0x0000, [destEndpoint: 0x01]) +	
//				zigbee.readAttribute(0x000C, 0x0055, [destEndpoint: 0x00]) + 
				zigbee.readAttribute(0x000C, 0x0055, [destEndpoint: 0x01]) + 
//				zigbee.readAttribute(0x000C, 0x0055, [destEndpoint: 0x02]) + 
//				zigbee.readAttribute(0x000C, 0x0055, [destEndpoint: 0x01, mfgCode: 0x115F]) + 
//				zigbee.readAttribute(0x000C, 0x0055, [destEndpoint: 0x02, mfgCode: 0x115F]) + 
//				zigbee.readAttribute(0x000C, 0x0055, [destEndpoint: 0x03, mfgCode: 0x115F]) 
				zigbee.readAttribute(0x000C, 0x001C, [destEndpoint: 0x01]) + // Description
				zigbee.readAttribute(0x000C, 0x0041, [destEndpoint: 0x01]) + // MaxPresentValue
//				zigbee.readAttribute(0x000C, 0x0045, [destEndpoint: 0x01]) + // MinPresentValue
//				zigbee.readAttribute(0x000C, 0x006A, [destEndpoint: 0x01]) + // Resolution
				zigbee.readAttribute(0x0010, 0xFF06, [destEndpoint: 0x01, mfgCode: 0x115F]) + // Interlock mode
				zigbee.readAttribute(0x0B04, 0x0000, [destEndpoint: 0x01]) +
//				zigbee.readAttribute(0x0B04, 0x0005, [destEndpoint: 0x01]) + // AC (Single Phase or Phase A) Measurements
//				zigbee.readAttribute(0x0B04, 0x0006, [destEndpoint: 0x01]) + // AC Formatting
				zigbee.readAttribute(0x0B04, 0x0300, [destEndpoint: 0x01]) + // ACFrequency
				zigbee.readAttribute(0x0B04, 0x0304, [destEndpoint: 0x01]) + // TotalActivePower
//				zigbee.readAttribute(0x0B04, 0x0305, [destEndpoint: 0x01]) +
//				zigbee.readAttribute(0x0B04, 0x0306, [destEndpoint: 0x01]) +
//				zigbee.readAttribute(0x0B04, 0x0400, [destEndpoint: 0x01]) +
//				zigbee.readAttribute(0x0B04, 0x0402, [destEndpoint: 0x01]) +
//				zigbee.readAttribute(0x0B04, 0x0403, [destEndpoint: 0x01]) +
//				zigbee.readAttribute(0x0B04, 0x0501, [destEndpoint: 0x01]) +
				zigbee.readAttribute(0x0B04, 0x0505, [destEndpoint: 0x01]) + // RMSVoltage
//				zigbee.readAttribute(0x0B04, 0x0506, [destEndpoint: 0x01]) +
				zigbee.readAttribute(0x0B04, 0x0508, [destEndpoint: 0x01]) + // RMSCurrent
				zigbee.readAttribute(0x0B04, 0x050B, [destEndpoint: 0x01]) + // ActivePower
				zigbee.readAttribute(0x0B04, 0x050B, [destEndpoint: 0x01, mfgCode: 0x115F]) +
//				zigbee.readAttribute(0x0B04, 0x050E, [destEndpoint: 0x01]) +
//				zigbee.readAttribute(0x0B04, 0x050F, [destEndpoint: 0x01]) +
				zigbee.readAttribute(0x0B04, 0x0510, [destEndpoint: 0x01])  // PowerFactor
//				zigbee.readAttribute(0x0B04, 0x0600, [destEndpoint: 0x01]) +
//				zigbee.readAttribute(0x0B04, 0x0601, [destEndpoint: 0x01]) 

	def actions = []
	cmds?.each {
		actions << new physicalgraph.device.HubAction(it)
	}

	sendHubCommand(actions, 2000)
}


/* 
 *  Children API
 */

def childUpdated(dni) {
	logDebug "childUpdated(${dni})"
	
	def child = findChildByDeviceNetworkId(dni)
	def endPoint = getEndPoint(dni)
	def nameAttr = "sw${endPoint}Name"
	logDebug "${child.displayName} vs ${device.currentValue(nameAttr)}"
	if (child && "${child.displayName}" != "${device.currentValue(nameAttr)}") {
		sendEvent(name: nameAttr, value: child.displayName, displayed: false)
	}
}

def childOn(dni) {
    
	def endPoint = getEndPoint(dni)
	
	logDebug "childOn: dni=${dni} endPoint=${endPoint}..."
	
	if ( (endPoint == 1) || (endPoint == 2) ) {
		//zigbee.on()
		def actions = [new physicalgraph.device.HubAction("st cmd 0x${device.deviceNetworkId} 0x0${endPoint} 0x0006 0x01 {}")]    
		sendHubCommand(actions)	
	}
	else {
		logError "childOn failed: Invalid endpoint: dni=${dni} endPoint=(${endPoint})"
	}
}


def childOff(dni) {
	
	def endPoint = getEndPoint(dni)
	
	logDebug "childOff: dni=${dni} endPoint=${endPoint}..."

	if ( (endPoint == 1) || (endPoint == 2) ) {
		//zigbee.off()
		def actions = [new physicalgraph.device.HubAction("st cmd 0x${device.deviceNetworkId} 0x0${endPoint} 0x0006 0x00 {}")]    
		sendHubCommand(actions)	
	}
	else {
		logError "childOn failed: Invalid endpoint: dni=${dni} endPoint=(${endPoint})"
	}
}

def childRefresh(dni) {
	logDebug "childRefresh(): child(${dni})"
	// TODO: test it
	
	def endPoint = getEndPoint(dni)
	
	def Cmds = zigbee.readAttribute(0x0001, 0x0000, [destEndpoint: 0x01]) +		// voltage
			   zigbee.readAttribute(0x0002, 0x0000, [destEndpoint: 0x01]) +		// temperature
			   zigbee.readAttribute(zigbee.ONOFF_CLUSTER, 0x0000, [destEndpoint: endPoint])	// switch state
	
	def actions = []
	
    Cmds?.each {
		actions << new physicalgraph.device.HubAction(it)
	}
	sendHubCommand(actions, 100)
	return []
}


/* 
 *  Children Helpers
 */

private createChildDevices() {
	logDebug "createChildDevices()... device=${device}"
	
	(1..2).each { endPoint ->
		if (!findChildByEndPoint(endPoint)) {			
			def dni = "${getChildDeviceNetworkId(endPoint)}"
			
			addChildSwitch(dni, endPoint)
			childUpdated(dni)
		}
	}
}

private addChildSwitch(dni, endPoint) {
	logDebug "Creating SW${endPoint} Child Device  device=${device}"
	try {
		def newChild = addChildDevice("Leza", "Aqara LLKZMK11LM 2 Channel Zigbee Relay Child", dni, null, 
			[
				completedSetup: true,
				label: "${device.displayName}-SW${endPoint}",
				isComponent: false
			]
		)
		//newChild.sendEvent(name:"switch", value:"off")
	} catch(e) {
	   logError "addChildSwitch failed: ${e}"
	}
	
}

private findChildByEndPoint(endPoint) {
	def dni = "${getChildDeviceNetworkId(endPoint)}"
	return findChildByDeviceNetworkId(dni)
}

private findChildByDeviceNetworkId(dni) {
	return childDevices?.find { it.deviceNetworkId == dni }
}

private getEndPoint(childDeviceNetworkId) {
	return safeToInt("${childDeviceNetworkId}".reverse().take(1))
}

private getChildDeviceNetworkId(endPoint) {
	return "${device.deviceNetworkId}-SW${endPoint}"
}



/* 
 *  Helpers
 */

def getDataTypeLength(type) {
	int len = 0
		
	switch (type) {
		case 0x08: // 8 bit data
		case 0x18: // 8 bit bitmap
		case 0x10: // boolean
		case 0x20: // uint8
		case 0x28:
			len = 1
		break
		case 0x09: // 16 bit data
		case 0x19:
		case 0x21: // uint16
		case 0x29:
		case 0x38: // floating point
			len = 2
		break
		case 0xa:
		case 0x1a:
		case 0x22:
		case 0x2a:
			len = 3
		break
		case 0x0b:
		case 0x1b:
		case 0x23: // Unsigned 32-bit integer
		case 0x2b:
		case 0x39: // floating point
			len = 4
		break
		case 0xc:
		case 0x1c:
		case 0x24:
		case 0x2c:
			len = 5
		break
		case 0xd:
		case 0x1d:
		case 0x25:
		case 0x2d:
			len = 6
		break
		case 0x0e:
		case 0x1e:
		case 0x26:
		case 0x2e:
			len = 7
		break
		case 0x0f:
		case 0x1f:
		case 0x27:
		case 0x2f:
		case 0x3a: //floating point
			len = 8
		break
	}
	//logDebug "getDataTypeLength type=${type} len=${len}"	
	return len
}

def parseValue(data_type, event_data, record)
{
	//logDebug "parseValue(): type=${data_type} data=${event_data}"	

	switch (data_type) {
		case 0x08: // 8 bit data
		case 0x18: // 8 bit bitmap
		//TODO
		break
		case 0x10: // boolean 
			record.value = event_data[0]
		break
		case 0x20: // uint8   
			//zigbee.convertHexToInt
			record.value = Integer.parseInt(event_data[0], 16)
		break
		case 0x28: //int8
			record.value = Integer.parseInt(event_data[0], 16)
			if (record.value > 127) {
				record.value = record.value - 256
			}
		break
		case 0x09: // 16 bit data
		case 0x19:
		//TODO
		break
		case 0x21: // uint16
			// TODO avoid string concatenation
			record.value = Integer.parseInt(event_data[1] + event_data[0], 16)
			break
		case 0x29:
			record.value = Integer.parseInt(event_data[1] + event_data[0], 16)
			if (record.value > 0x7FFF) {
				record.value = 0x10000 - record.value
			}
			break
		case 0x38: // floating point
		break
		case 0xa:
		case 0x1a:
		case 0x22:
		case 0x2a:
		break
		case 0x0b: // 4 byte
		case 0x1b: // 32 bit bitmap
		case 0x23: // unit32
		case 0x2b: // int32
			record.value = Integer.parseInt(event_data[3] + event_data[2] + event_data[1] + event_data[0], 16)
		break
		case 0x39: // floating point
			record.value = Float.intBitsToFloat(Long.parseLong(event_data[3] + event_data[2] + event_data[1] + event_data[0], 16).intValue())			
		break
		case 0xc:
		case 0x1c:
		case 0x24:
		case 0x2c:
		break
		case 0xd:
		case 0x1d:
		case 0x25:
		case 0x2d:
		break
		case 0x0e:
		case 0x1e:
		case 0x26:
		case 0x2e:
		break
		case 0x0f:
		case 0x1f:
		case 0x27:
			record.value = Long.parseLong(event_data[7] + event_data[6] + event_data[5] + event_data[4] +
											   event_data[3] + event_data[2] + event_data[1] + event_data[0], 16)
			break
		case 0x2f:
		case 0x3a: //floating point
		break
	}
	//logDebug "parseValue done: " + record.dump()
	return record
}



def processRecord(record_type, data_type, record) {
	
	switch (record_type) {
		case 0x03: // temperature °C, type 0x28
			record.name = 'temperature'
			handleSetTemperatureEvent( record.value )
		break
		
		//case 0x05:   // ?  RSSI dB 	0x021 (UINT16)
		//	record.name = 'RSSI'
		//	sendEvent( name: record.name, value: record.value, unit: 'dB',   displayed: true )
		//break
				
		//case 0x07:  // 0x27 UINT64 ??
		//case 0x08:  // 0x21 UINT16 ??
		//case 0x09:  // 0x21 UINT16 ??
		//break
		
		//case 0x0A: // 0x0A 	ZigBee Parent Device Network ID 	0x021 (UINT16)
		//break
		
		case Key_SWITCH_1():
			record.name = 'switch'
			handleSwitchClusterEvent(1, 0x0000, 0, record.value)
		break
		
		case Key_SWITCH_2():
			record.name = 'switch'
			handleSwitchClusterEvent(2, 0x0000, 0, record.value)
		break
		
		//case 0x6E: // uint8 No idea what is it
		//case 0x6F: // uint8 No idea what is it
		//break
		
		//case 0x94:
		//break
		
		case Key_ENERGY_METER(): // 0x95 //energy
			record.name = 'energy'
			record.rounded_value = roundTwoPlaces(record.value)
			executeSendEvent(null, createEventMap(record.name, record.value, null, "kWh") )
		break
		
		case 0x96:
			record.name = 'voltage'
			record.rounded_value = roundTwoPlaces(record.value/10)
			executeSendEvent(null, createEventMap(record.name, record.rounded_value, null, "V"))
		break

		case 0x97:  // Current
			record.name = 'current'
			state.current = record.value // roundTwoPlaces(record.value)
			executeSendEvent(null, createEventMap(record.name, state.current, null, "A") )
		break

		case Key_POWER_METER(): //0x98
			record.name = 'power'
			executeSendEvent(null, createEventMap(record.name, record.value, null, "W") )
		break
		
		
		default:
			record.name = "unknown record (" + record_type + ")"
			//logDebug "Unknown record ${record}"
	}
}



def handleBasicClusterEvent(endpoint, attribute, command, descMap) {
	
	logDebug "handleBasicClusterEvent(): endpoint=${endpoint} attribute=${attribute} command=${command} "
    
    if ( descMap == null ) {
        logDebug "DESC IS NULL"
        return
    }
	
	List processed = []
	Map curr = [:]
	switch ( attribute )
	{
		case 0xFF01: //
		
			try {
				int i = 4
				while ( (i+2) < descMap.data.size() ) {

					curr = null
					int j = i
					int event_type  = zigbee.convertHexToInt(descMap.data[i++])
					int data_type	= zigbee.convertHexToInt(descMap.data[i++])

					int data_len 	= getDataTypeLength(data_type)

					def event_data = descMap.data[i..i+data_len-1]
					i += data_len

					Map record = [:]
					record.first = j
					record.last  = i
					record.event_type = descMap.data[j]   // event_type
					record.data_type  = descMap.data[j+1] // data_type
					record.data_len   = data_len

					record.data = event_data
					//logDebug "handleBasicClusterEvent process record=${j}..${i} record=0x" + descMap.data[j] + " data_type=0x" + descMap.data[j+1]


					curr = record
					record = parseValue(data_type, event_data, record)
					def decoded = record?.value

					processed += record

					//logDebug "handleBasicClusterEvent process record=${j}..${i} type=0x" + descMap.data[j] + " data_type=0x" + descMap.data[j+1] + " data_len=${data_len} decoded=${decoded} event_data=${event_data}"

					processRecord(event_type, data_type, record)
				}
			}
			catch (ex) {
				logDebug "handleBasicClusterEvent FAILED: " + processed + "--- VALUES --- " + curr
				logError "handleBasicClusterEvent: Exeption: $ex  "
			}
			logDebug "handleBasicClusterEvent done: ${processed}"
		break
		default:
			logInfo "Uknown attribute for basic cluster: attr=${attribute}" 
	}
}

def handleSetTemperatureEvent(int value) {

	try {
		logDebug "handleSetTemperatureEvent(): " + value
		state.temperature = value
		sendEvent( name: 'temperature', value: getTemperature(state.temperature), unit: getTempUnits(),   displayed: true )
	}
	catch (ex) {
		logError "handleSetTemperatureEvent: Exeption: $ex  "
	}
}

def handleSwitchClusterEvent(src, attribute, command, value) {

	logDebug "handleSwitchClusterEvent(): src=${src} attribute=${attribute} value=${value} " + value.dump()
	int endPoint = src as int
	List result = []

	switch ( attribute )
	{
		case 0x0000: // On/Off
			int index = endPoint - 1
			state.children[index] = (value == "01") ? "on" : "off"
			executeSendEvent(findChildByEndPoint(endPoint), createEventMap("switch", state.children[index]))
			executeSendEvent(null, createEventMap("sw${endPoint}Switch", state.children[index]))
			executeSendEvent(null, createEventMap("switch", ((state.children[0]  == "on") || (state.children[1] == "on")) ? "on" : "off"))
		break

		case 0x4000: // GlobalSceneControl
		break

		case 0x4001: // OnTime
		break

		case 0x4002: // OffWaitTime
		break
	}
//	logDebug "handleSwitchClusterEvent done: children=" + state.children.dump() + "  result: " + result.dump()
	return result
}


private executeSendEvent(child, evt) {
	
	if (evt.displayed == null)	evt.displayed = (getAttrVal(evt.name, child) != evt.value)

    
	if (evt) {
		if (child) {
			if (evt.descriptionText) {
				evt.descriptionText = evt.descriptionText.replace(device.displayName, child.displayName)
			}
			child.sendEvent(evt)						
		}
		else {
            sendEvent(evt)
		}		
	}
	//logDebug "executeSendEvent(): evt=${evt}"
}

private createEventMap(name, value, displayed=null, unit=null) {	
	def eventMap = [
		name: name,
		value: value,
		displayed: displayed,
		isStateChange: true,
		descriptionText: "${device.displayName} - ${name} is ${value}"
	]
	
	if (unit) {
		eventMap.unit = unit
		eventMap.descriptionText = "${eventMap.descriptionText} ${unit}"
	}
	return eventMap
}

private getAttrVal(attrName, child=null) {
	//logDebug "getAttrVal(): attrName=${attrName} child=${child}"
	
	try {
		if (child) {
			return child?.currentValue("${attrName}")
		}
		else {
			return device?.currentValue("${attrName}")
		}
	}
	catch (ex) {
		logError "getAttrVal: $ex"
		return null
	}
}


private safeToInt(val, defaultVal=0) {
	return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal
}

private safeToDec(val, defaultVal=0) {
	return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal
}

private roundTwoPlaces(val) {
	return Math.round(safeToDec(val) * 100) / 100
}

private isDuplicateCommand(lastExecuted, allowedMil) {
	!lastExecuted ? false : (lastExecuted + allowedMil > new Date().time) 
}

private hexStringToString(String s) {
    String converted = ""
    for (int i = 0; i< s.length(); i +=2)
    {
        converted = converted + (Integer.parseInt(s.substring(i,i+2),16) as char)
    }
    return converted
}


private logDebug(msg) {
	if (settings?.debugOutput != false) {
		log.debug "$msg"
	}
}

private logInfo (msg) {
	log.info "$msg"
}

private logError (msg) {
	log.error "$msg"
}


private updateHealthCheckInterval() {
	logDebug "updateHealthCheckInterval().."

	// Device wakes up every 5 min, this interval allows us to miss one wakeup notification before marking offline
	def minReportingInterval = (1 * 60 * 5)

	if (state.minReportingInterval != minReportingInterval) {
		state.minReportingInterval = minReportingInterval
		logDebug "Configured health checkInterval when updated()"

		// Set the Health Check interval so that it can be skipped twice plus 2 minutes.
		def checkInterval = ((minReportingInterval * 2) + (2 * 60))
		sendEvent(name: "checkInterval", value: checkInterval, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
	}
}

private getTempUnits() {
	def units = 'C'
	if (settings?.tempUnitDisplayed != "1" ) { 
		units = 'F'
	}
	return units
}
private getTemperature(int v) {
	try {
		int res = v

		int offset = 0
		if ( settings?.temperatureOffset != null )
		{
			offset = settings?.temperatureOffset
		}

		if (settings?.tempUnitDisplayed != "1" ) { 
			res = v*9/5+32
		}
		return res + offset
	}
	catch (ex) {
		logError "getTemperature: Exeption: $ex  "
	}
}

private getInterlockState() {
	String str = disabled
	switch ( state?.interlock ) {
		case 0:
			str = "disabled"
		break
		case 1:
			str = "enabled"
		break
		case 0x86:
			str = "failed to change"
		break
		default:
			str = "checking..."
	}
	return str
}

private getInterlockConfig() {
    int res = 0
    if ( settings?.interlock ) {
		res = 1
    }
	return res
}

