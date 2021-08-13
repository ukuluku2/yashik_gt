/*
 * Smartthings DH for multy relay module
 * Product: TS0601
 *
 * Supported devices:
 *     SmartHome4U light/curtain module
 *     Moes Multi gang Wall Touch Switch
 *
 * Yashik, Jul-2021
 *
 * References:
 * 	https://github.com/iquix/Smartthings/blob/master/devicetypes/iquix/tuya-window-shade.src/tuya-window-shade.groovy
 * 	https://github.com/martinkura-svk/Hubitat/blob/main/Moes%20ZigBee%20Wall%20Switch
 * 	https://developer.tuya.com/en/docs/iot/tuya-zigbee-universal-docking-access-standard?id=K9ik6zvofpzql
 * 	https://community.hubitat.com/t/tuya-zigbee-trv/57787

 zbjoin: {"dni":"6C87","d":"847127FFFE0EFFCB","capabilities":"8E",
    "endpoints":[{"simple":"01 0104 0051 00 05 0000 000A 0004 0005 EF00 01 0019", "application":"52","manufacturer":"_TZE200_5apf3k9b","model":"TS0601"}],
    "parent":"0000","joinType":1,"joinDurationMs":3982,"joinAttempts":1}

 zbjoin: {“dni”:“F208”,“d”:“84FD27FFFE60361F”,“capabilities”:“80”,
    “endpoints”:[{“simple”:“01 0104 0051 01 04 0000 0004 0005 EF00 02 0019 000A”,“application”:“42”,“manufacturer”:"_TZE200_tz32mtza",“model”:“TS0601”}],
    “parent”:0,“joinType”:1,“joinDurationMs”:2560,“joinAttempts”:1}

 *
 * Frame
 * |  1 byte | 2 bytes         | DP Data ( N bytes )                         |
 * | Command | Sequence number | DPID 1b | type 1b | len 2 b | value         |
 *
 *
 * Commands
 * TY_DATA_REQUEST 	0x00 	Gateway-side data request
 * TY_DATA_RESPONE 	0x01 	Reply to MCU-side data request
 * TY_DATA_REPORT 	0x02 	MCU-side data active upload (bidirectional)
 * TY_DATA_QUERY 	0x03 	GW send, trigger MCU side to report all current information, no zcl payload.
 *                          Note: Device side can make a policy, data better not to report centrally
 *
 * DP Data Format
 *   DPID 1 byte
 *   type    0x00 raw
 *           0x01 bool
 *           0x02 uint32 (big endian)
 *           0x03 string (N bytes)
 *           0x04 enum 1 byte
 *           0x05 bitmap 1/2/4 bytes
 *   len     2 bytes
 *   value   1/2/4/N
 *
 *
 * DPs
 * | DP   | Function        | Value             | Read/Write |
 * | 0x01 | switch_1        | 0 - off, 1 - on   | R/W        |
 * | 0x02 | switch_2        | 0 - off, 1 - on   | R/W        |
 * | 0x0D | switch_all      | 0 - off, 1 - on   | R/W        |
 * | 0x10 | backlight       | 0 - off, 1 - on   | R/W        |
 * | 0x65 | childlock       | 0 - off, 1 - on   | R/W        |
 * |      |(disable switch) |                   |            |
 *
 * | 0x66 | tt_switch       | 0 - off, 1 - on   | R/W        |
 * | 0x6F | deviceType_1    | 0-light 1-curtain | RO         |
 * | 0x79 | curtian_1       | 0:stop            | WO         |
 * |      |                 | 1:open 5 mintues  |            |
 * |      |                 | 2: close 5 minutes|            |
 * |      |                 | 3:open tt time    |            |
 * |      |                 | 4:close tt time   |            |
 */

metadata {
	definition (name: "TS0601 Relay", namespace: "Yashik", author: "Yashik", vid:"generic-switch") {
		capability "Actuator"
		capability "Switch"
		capability "Health Check"
		capability "Refresh"

		(1..2).each {
			attribute "sw${it}Switch", "string"
			attribute "sw${it}Name", "string"
		}
		attribute "Info", "string"

        fingerprint profileId:      "0104", deviceId: "0051",
                    inClusters:     "0000 000A 0004 0005 EF00",
                    outClusters:    "0019",
                    manufacturer:   "_TZE200_5apf3k9b", model: "TS0601", deviceJoinName: "TS0601 Relay"

        fingerprint profileId:      "0104",
                    deviceId:       "0051",
                    inClusters:     "0000 0004 0005 EF00",
                    outClusters:    "0019 000A",
                    application:    "42",
                    manufacturer:   "_TZE200_tz32mtza", model: "TS0601", deviceJoinName: "Moes multi gang switch"
	}

	preferences {
		input name: "debugOutput", type: "bool", title: "Enable Debug Logging", description: "Control logs verbosity", defaultValue: true, required: true, displayDuringSetup:false
		input name: "childLock", type: "bool", title: "Child lock", description: "Prevents accidential use", defaultValue: false, required: true, displayDuringSetup:false
		input name: "refreshActions", type: "bool", title: "Refresh info", description: "Sends queries to the device", defaultValue: false, required: false, displayDuringSetup:false
		input name: "numOfChildren", type: "decimal", title: "Gang count", defaultValue: 2, range: "1..6", description: "Number of gangs", required: true, displayDuringSetup:false
		input name: "backlight", type: "bool", title: "Backlight", description: "Controls Backlight", defaultValue: false, required: true, displayDuringSetup:false
	}
}


/*
 * DH API implementation
 */
def installed() {
	logDebug "installed()... DeviceId : ${device.id}, manufacturer: ${device.getDataValue('manufacturer')}, model: ${device.getDataValue('model')} settings=${settings}"

	state.children = [ 0, 0, 0, 0, 0, 0 ]
	state.info = [ : ]
	state.packetID = 0

	createRemoveChildDevices()
}

def configure() {
	logDebug "configure()... settings=${settings} childDevices=${childDevices}"

	def cmds = []
	cmds +=
			zigbee.readAttribute(0x0000, 0x0000, [destEndpoint: 0x01]) + // ZCLVersion
			zigbee.readAttribute(0x0000, 0x0001, [destEndpoint: 0x01]) + // ApplicationVersion
			zigbee.readAttribute(0x0000, 0x0002, [destEndpoint: 0x01]) + // StackVersion
			zigbee.readAttribute(0x0000, 0x0003, [destEndpoint: 0x01]) + // HWVersion
			zigbee.readAttribute(0x0000, 0x0004, [destEndpoint: 0x01]) + // ManufactureName
			zigbee.readAttribute(0x0000, 0x0005, [destEndpoint: 0x01]) + // Model Identifier
			zigbee.readAttribute(0x0000, 0x0006, [destEndpoint: 0x01])   // DateCode


	sendCommandsToDevice(cmds, 400)

	runIn(1, refreshAction)
}


def updated() {
	logDebug "updated()... device=${device}  settings=${settings} DNI=${device.deviceNetworkId}"

	try {
		createRemoveChildDevices()

		if (settings?.refreshActions == true)
		{
			device.updateSetting('refreshActions', false)
			refreshAction()
			return
		}

		//  Child lock
		def cmds = createTuyaCommand(0x65, DP_TYPE_BOOL, zigbee.convertToHexString((settings?.childLock == true ? 1 : 0),2) )
		cmds += createTuyaCommand(0x10, DP_TYPE_BOOL, zigbee.convertToHexString((settings?.backlight == true ? 1 : 0),2) )
		sendCommandsToDevice(cmds, 300)
	}
	catch (ex) {
		logError "updated: Exeption: $ex  "
	}
}


def parse(String description) {

	try {
		def descMap = zigbee.parseDescriptionAsMap(description)
		logInfo "parse(): description is '$description'  descMap is $descMap"

		def command  = descMap?.command

		if (description?.startsWith("read attr -")) {
			switch (descMap?.clusterInt) {
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
						case 0x0004:
							state.info.ManufactureName = hexStringToString(descMap.value)
						break
						case 0x0005:
							state.info.Model = hexStringToString(descMap.value)
						break
						case 0x0006:
							state.info.DateCode = hexStringToString(descMap.value)
						break
						default:
						    state.info["Cluster 0x0000 " + descMap?.attrInt] = descMap.value
					}
					sendEvent(name: "Info", value: state.info)
				break

				break
				case 0xEF00: // Tuya specific cluster
					if ( descMap?.command == "01" ) {
						// 2b packet id, 1b dp, 1b type, 2b len, 1b value
						handleSwitchEvent( zigbee.convertHexToInt(descMap?.data[2]), zigbee.convertHexToInt(descMap?.data[6]))
					}
				break
			}
		} else if (description?.startsWith("catchall:")) {
			switch (descMap?.clusterInt) {
				case 0xEF00:
					if ( descMap?.command == "01" ) {
						// 2b packet id, 1b dp, 1b type, 2b len, 1b value
						def dp = zigbee.convertHexToInt(descMap?.data[2])
						def value = zigbee.convertHexToInt(descMap?.data[6])
						switch (dp) {
							case 1: // Switch 1
							case 2: // Switch 2
							case 3:
							case 4:
							case 5:
							case 6:
							case 0xD:
								handleSwitchEvent( dp, value)
							break
							case 0x65: // Child lock
								//device.updateSetting('childLock', (value == 0) ? false : true )
								state.info.ChildLock = (value == 0) ? "off" : "on"
								sendEvent(name: "Info", value: state.info)
							break
							case 0x6F:
								state.info.DeviceMode = (value == 0) ? "light" : "curtain"
								sendEvent(name: "Info", value: state.info)
							break
						}
					}

					// Handle only TY_DATA_RESPONE and TY_DATA_REPORT
					if ( descMap?.command == "01" || descMap?.command == "02" ) {

					}
				break
			}
		}
	}
	catch (ex) {
		logError "parse: Exeption: $ex  "
	}
}


/*
 * Actions
 */

def on() {
	logDebug "on()..."

	parentOnOff(true)
}

def off() {

	logDebug "off()..."

	parentOnOff(false)
}


def refresh() {
	logDebug "refresh()..."
	// TODO test it
	def cmds =
                zigbee.readAttribute(zigbee.ONOFF_CLUSTER, 0x0000, [destEndpoint: 0x01]) +		// switch 1 state
                zigbee.readAttribute(zigbee.ONOFF_CLUSTER, 0x0000, [destEndpoint: 0x02]) +		// switch 2 state
				zigbee.readAttribute(0xEF00, 0x0000, [destEndpoint: 0x01]) +		// switch 2 state

				"st cmd 0x${device.deviceNetworkId} 0x01 0xEF00 0x03 {}" +  // Query?
				"st cmd 0x${device.deviceNetworkId} 0x01 0xEF00 0x00 {}" +

				"st cmd 0x${device.deviceNetworkId} 0x01 0xEF00 0 0x00 0 0xFFFF {}" // <Device id> <EP> <Cluster> <Dst>
	return cmds
}

def refreshAction() {

	logDebug "refreshActions()..."

	sendCommandsToDevice(refresh(), 300)
}

/*
 *  Children API
 */

def childUpdated(dni) {
	logDebug "childUpdated(${dni})"

	def child = findChildByDeviceNetworkId(dni)
	def endPoint = getEndPoint(dni)
	def nameAttr = "sw${endPoint}Name"
	logDebug "${child?.displayName} vs ${device.currentValue(nameAttr)}"
	if (child && ("${child?.displayName}" != "${device.currentValue(nameAttr)}") ) {
		sendEvent(name: nameAttr, value: child.displayName, displayed: false)
	}
}

def childOnOff(String dni, boolean turnOn) {

	try {
		def endPoint = getEndPoint(dni)
		def cmds = createChildOnOffCommand(endPoint,turnOn)

		logDebug "childOnOff(): endPoint=${endPoint} turnOn=${turnOn} cmds=${cmds}"

		sendCommandsToDevice(cmds, 300)

	} catch(e) {
	   logError "childOnOff failed: ${e}"
	}
}


/*
 *  Children Helpers
 */

private createRemoveChildDevices() {
	logDebug "createRemoveChildDevices()... gangs=" + getGangCount() + " device=" + device.dump()

	try {
		// Update DNIs if neccessary
		for (child in childDevices) {
			if ( !child.deviceNetworkId.startsWith("${device.deviceNetworkId}-SW") ) {
				try {
					def newDNId = getChildDeviceNetworkId( getEndPoint(child.deviceNetworkId) )
					logInfo "Updating child DNI: ${child.deviceNetworkId} -> ${newDNId}"
				    child.setDeviceNetworkId(newDNId)
					child?.sendEvent(name: "deviceNetworkId", value: newDNId)
				} catch(e) {
					logError "createRemoveChildDevices failed to update, removing: ${child?.deviceNetworkId} ${e}"
					deleteChildDevice( child?.deviceNetworkId )
				}
			} else {
				logDebug "child = " + child.dump()
			}
		}

		def count = getGangCount()

		def firstToCreate = 1
		def firstToDelete = count + 1

		if ( count <= 1 ) {
			// If only a single gang, remove all children and make sure none is created
			firstToCreate = 2
			firstToDelete = 1
		}

		// Delete unused devices
		(firstToDelete..6).each { endPoint ->
			def child = findChildByEndPoint(endPoint)
			if (child) {
				deleteChildDevice(child?.deviceNetworkId)
			}
		}

		// Create required children device if there are more than 1
		(firstToCreate..count).each { endPoint ->
			def dni = "${getChildDeviceNetworkId(endPoint)}"
			if (!findChildByDeviceNetworkId(dni)) {
				addChildSwitch(dni, endPoint)
				childUpdated(dni)
			}
		}
	} catch(e) {
	   logError "createRemoveChildDevices failed: ${e}"
	}
}

private addChildSwitch(dni, endPoint) {
	logDebug "Creating SW${endPoint} Child Device  device=${device}"
	try {
		def newChild = addChildDevice("Yashik", "TS0601 Relay Child", dni, null,
			[
				completedSetup: true,
				label: "${device.displayName}-SW${endPoint}",
				isComponent: false
			]
		)
	} catch(e) {
	   logError "addChildSwitch failed: ${e}"
	}
}

private getChildDeviceNetworkId(endPoint) {
	return "${device.deviceNetworkId}-SW${endPoint}"
}

private findChildByDeviceNetworkId(dni) {
	return childDevices?.find { it.deviceNetworkId == dni }
}

private findChildByEndPoint(endPoint) {
	def dni = "${getChildDeviceNetworkId(endPoint)}"
	return findChildByDeviceNetworkId(dni)
}

private getEndPoint(childDeviceNetworkId) {
	return safeToInt("${childDeviceNetworkId}".reverse().take(1))
}

private safeToInt(val, defaultVal=0) {
	return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal
}

def createChildOnOffCommand(int endPoint, boolean turnOn) {

	def cmd

	if ( ((endPoint >= 1) && (endPoint <= 6)) || endPoint == 0xD ) {
		//cmd = createTuyaCommand(zigbee.convertToHexString(endPoint,2), DP_TYPE_ENUM, zigbee.convertToHexString((turnOn ? 1 : 0),2))
		cmd = createTuyaCommand(endPoint, DP_TYPE_BOOL, zigbee.convertToHexString((turnOn ? 1 : 0),2) )
	}
	else {
		logError "childOn failed: Invalid endpoint: dni=${dni} endPoint=(${endPoint})"
	}
	return cmd
}

def parentOnOff(boolean turnOn) {
	logDebug "on()..."

	def cmds = []

	if ( isMoesSwitch() ) {
		(1..getGangCount()).each { endPoint ->
			cmds += createChildOnOffCommand(endPoint,turnOn)
		}
	}
	else {
		cmds += createChildOnOffCommand(0xD,turnOn)
	}

	sendCommandsToDevice(cmds, 500)
}


/*
 *  Helpers
 */

def handleSwitchEvent(int endPoint, int value) {

	try {
		if ( (endPoint >= 1) && (endPoint <= getGangCount()) ) {
			int index = endPoint - 1
			state.children[index] = value

			def main_value = state?.children[0]
			(2..getGangCount()).each { dp ->
				main_value += state?.children[dp-1]
			}

			logDebug "handleSwitchEvent(): EP=${endPoint} value=${value}  children=${state.children}  main=${main_value}"

			sendEvent( name: 'switch',              value: getOnOffStr(main_value),            displayed: true )
			sendEvent( name: "sw${endPoint}Switch", value: getOnOffStr(state.children[index]), displayed: true )

			def child = findChildByEndPoint(endPoint)

			child?.sendEvent( name: 'switch', value: getOnOffStr(value), displayed: true )
		} else if ( endPoint == 0xD ) {
			sendEvent( name: 'switch',              value: getOnOffStr(value),            displayed: true )
		/*
			(1..getGangCount()).each { dp ->
				sendEvent( name: "sw${dp}Switch", value: getOnOffStr(value), displayed: true )
				state.children[dp-1] = value
				def child = findChildByEndPoint(dp)
				child?.sendEvent( name: 'switch', value: getOnOffStr(value), displayed: true )
			}
		*/
			logDebug "handleSwitchEvent(): EP=${endPoint} value=${value}  child0=${state.children[0]} child1=${state.children[1]} main=${value}"
		}
	}
	catch (ex) {
		logError "handleSwitchEvent: Exeption: $ex  "
	}
}

private getOnOffStr(int val) {
	return (val>0) ? "on" : "off"
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

private int getGangCount() {

	int v = safeToInt(settings?.numOfChildren,2)
	if ( v == 0 ) {
	    v = 2
	}
	return v
}

private boolean isMoesSwitch() {
    return device.getDataValue("manufacturer") == "_TZE200_tz32mtza"
}


private getCLUSTER_TUYA()  { 0xEF00 }
private getSETDATA()       { 0x00 }

// tuya DP type
private getDP_TYPE_BOOL()  { "01" }
private getDP_TYPE_VALUE() { "02" }
private getDP_TYPE_ENUM()  { "04" }

private getPACKET_ID() {
	state.packetID = ((state.packetID ?: 0) + 1 ) % 65536
	return zigbee.convertToHexString(state.packetID, 4)
}


private createTuyaCommand(int dpId, dp_type, value) {

	try {

		def packetId = PACKET_ID
		def dp       = zigbee.convertToHexString(dpId,2)
		def lenStr   = zigbee.convertToHexString(value.length()/2, 4)

		def cmd = zigbee.command(CLUSTER_TUYA, SETDATA, packetId + dp + dp_type + lenStr + value )
		//[st cmd 0x205C 0x01 0xEF00 0x00 {00040101000100}, delay 2000]
		//def cmd2 = "st cmd 0x${device.deviceNetworkId} 0x01 0xEF00 0x00 {${packetId}${dp}${dp_type}${lenStr}${value}}"

		//logDebug "createTuyaCommand(): dp=${dp} type=${dp_type} len=${lenStr} cmd=${value} -> cmd1=${cmd} cmd2=${cmd2}"
		logDebug "createTuyaCommand(): dp=${dp} type=${dp_type} len=${lenStr} value=${value} -> cmd=${cmd}"

		return cmd
	}
	catch (ex) {
		logError "createTuyaCommand: Exeption: $ex  "
	}
	return ""
}


private sendCommandsToDevice(cmds, delay) {

	try {
		//cmds.removeAll { it.startsWith("delay") }
		def actions = []
		cmds?.each {
			actions << new physicalgraph.device.HubAction(it)
		}
		logDebug "sendCommandsToDevice(): delay=${delay} cmds=${cmds} actions=${actions}"

		sendHubCommand(actions, delay)
	}
	catch (ex) {
		logError "sendCommandsToDevice: Exeption: $ex  "
	}
}
