/*
 * Smartthings DH for multy relay module - child
 * Product: TS0601
 *
 * Yashik, Jul-2021
 */

metadata {

	definition (name: "TS0601 Relay Child", namespace: "Yashik", author: "Yashik", vid:"generic-switch") {
		capability "Actuator"
		capability "Switch"
		capability "Polling"
		capability "Sensor"
		capability "Refresh"
		capability "Health Check"

        command    "refresh"
	}
}

def parse(String description) {
	try {
		logDebug "child parse(): description is $description"
		def cluster = zigbee.parseDescriptionAsMap(description)
		logDebug "parse: descMap is $cluster"
	} catch(e) {
		logDebug "child parse() failed: ${e}"
	}
}

def installed() {
	logDebug "child installed: ${device}"
}

def updated() {
	logDebug "child updated: " + device.dump()
	parent.childUpdated(device.deviceNetworkId)
}

def on() {
	logDebug "child on: " + device.dump() + " parent=" + parent.dump()
    if ( parent ) {
		parent.childOnOff(device.deviceNetworkId, true)
    }
}

def off() {
	logDebug "child off: " + device.dump() + " parent=" + parent.dump()
	parent.childOnOff(device.deviceNetworkId, false)
}

def refresh() {
	logDebug "child refresh: ${device}"
	parent.childRefresh(device.deviceNetworkId)
}

private logDebug(msg) {
    if ( parent ) {
		parent.logDebug("$msg")
	} else {
		log.debug "${msg}"
	}
}
