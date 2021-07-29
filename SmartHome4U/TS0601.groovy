/*
 * Smartthings DH for dual relay module
 * Product: TS0601
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
 */

metadata {
	definition (name: "TS0601 Relay", namespace: "Yashik", author: "Yashik", vid:"generic-switch") {
		capability "Actuator"
		capability "Switch"
		capability "Health Check"


		(1..2).each {
			attribute "sw${it}Switch", "string"
			attribute "sw${it}Name", "string"
		}
		attribute "Info", "string"

		fingerprint	profileId: 		"0104", deviceId: "0051",
					inClusters: 	"0000 000A 0004 0005 EF00",
					outClusters: 	"0019",
					manufacturer: 	"_TZE200_5apf3k9b", model: "TS0601", deviceJoinName: "TS0601 Relay"
	}

	preferences {
		input name: "debugOutput", type: "bool", title: "Enable Debug Logging", description: "Control logs verbosity", defaultValue: true, required: true, displayDuringSetup:false
		input name: "refreshActions", type: "bool", title: "Refresh info", description: "Sends queries to the device", defaultValue: false, required: false, displayDuringSetup:false
	}
}


/*
 * DH API implementation
 */
def installed() {
	logDebug "installed()... device=${device} settings=${settings}"

	state.children = [ 0, 0 ]
	state.info = [ : ]
	state.packetID = 0

	createChildDevices()
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


	sendCommandsToDevice(cmds, 250)

	runIn(1, refreshAction)
}


def updated() {
	logDebug "updated()... device=${device} settings=${settings} "

	try {
		if (settings?.refreshActions == true)
		{
			device.updateSetting('refreshActions', false)
			refreshAction()
			return
		}
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
			switch (descMap.clusterInt) {
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
						handleSwitchEvent( zigbee.convertHexToInt(descMap?.data[2]), zigbee.convertHexToInt(descMap?.data[6]))
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

	def cmds = []
	cmds += createChildOnOffCommand(1,true)
	cmds += createChildOnOffCommand(2,true)

	sendCommandsToDevice(cmds, 250)
}

def off() {

	try {
		logDebug "off()..."

		def cmds = []

		cmds += createChildOnOffCommand(1,false)
		cmds += createChildOnOffCommand(1,false)

		sendCommandsToDevice(cmds, 250)
	}
	catch (ex) {
		logError "off: Exeption: $ex  "
	}
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

	sendCommandsToDevice(refresh(), 250)
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

def childOnOff(String dni, boolean turnOn) {

	try {
		def endPoint = getEndPoint(dni)
		def cmds = createChildOnOffCommand(endPoint,turnOn)

		logDebug "childOnOff(): endPoint=${endPoint} turnOn=${turnOn} cmds=${cmds}"
		sendCommandsToDevice(cmds, 100)

	} catch(e) {
	   logError "childOnOff failed: ${e}"
	}
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
	try {

		//logDebug "createChildOnOffCommand(): ->" +(turnOn ? "childOn" : "childOff") + ": endPoint=${endPoint}..."

		if ( (endPoint == 1) || (endPoint == 2) ) {
			//cmd = createTuyaCommand(zigbee.convertToHexString(endPoint,2), DP_TYPE_ENUM, zigbee.convertToHexString((turnOn ? 1 : 0),2))
			cmd = createTuyaCommand(zigbee.convertToHexString(endPoint,2), DP_TYPE_BOOL, zigbee.convertToHexString((turnOn ? 1 : 0),2) )
		}
		else {
			logError "childOn failed: Invalid endpoint: dni=${dni} endPoint=(${endPoint})"
		}
	}
	catch (ex) {
		logError "on: Exeption: $ex  "
	}
	return cmd
}



/*
 *  Helpers
 */

def handleSwitchEvent(int endPoint, int value) {

	try {
		if ( (endPoint >= 1) && (endPoint <= 2) ) {
			int index = endPoint - 1
			state.children[index] = value

			def main_value = state?.children[0] + state?.children[1]
			logDebug "handleSwitchEvent(): EP=${endPoint} value=${value}  child0=${state.children[0]} child1=${state.children[1]} main=${main_value}"

			sendEvent( name: 'switch',              value: getOnOffStr(main_value),            displayed: true )
			sendEvent( name: "sw${endPoint}Switch", value: getOnOffStr(state.children[index]), displayed: true )

			def child = findChildByEndPoint(endPoint)

			child?.sendEvent( name: 'switch', value: getOnOffStr(value), displayed: true )
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


// Control enum Values:
// open - 0
// pause - 1
// close - 2
// createTuyaCommand("01", DP_TYPE_ENUM, zigbee.convertToHexString(0))
//

private createTuyaCommand(dp, dp_type, fncmd) {
	def packetId = PACKET_ID
	logDebug "createTuyaCommand(): dp=${dp} type=${dp_type} cmd=${fncmd} ->" + zigbee.convertToHexString(CLUSTER_TUYA) + zigbee.convertToHexString(SETDATA) + packetId + dp + dp_type + zigbee.convertToHexString(fncmd.length()/2, 4) + fncmd
	return zigbee.command(CLUSTER_TUYA, SETDATA, packetId + dp + dp_type + zigbee.convertToHexString(fncmd.length()/2, 4) + fncmd )
}


private sendCommandsToDevice(cmds, delay) {

	def actions = []
	cmds?.each {
		actions << new physicalgraph.device.HubAction(it)
	}
	sendHubCommand(actions, delay)
}
