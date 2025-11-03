/* Authored by iqbserve.de */

import { WsoCommonMessage } from '../jsmod/data-classes.mjs';
import { WorkbenchInterface as WbApp } from '../jsmod/workbench.mjs';

/**
 * A simple WebSocket implementation for the workbench app.
 * 
 * The Data-IO uses WsoCommonMessage objects
 * that are serialized/deserialized to+from JSON.
 */

let hostUrl = null;
let websocket = null;
let listener = { "any": [] };

export function initialize(wsodata) {
	hostUrl = wsodata.hostUrl;
	listener["any"] = [];
};

export function isConnected() {
	return !(websocket == null || websocket.readyState == WebSocket.CLOSED);
};

export function connect() {
	if (!isConnected()) {
		websocket = new WebSocket(hostUrl);

		// Event listener 
		websocket.onopen = function (event) {
			console.log("WebSocket connection [opened]");
		};

		websocket.onmessage = function (event) {
			onMessage(event);
		};

		websocket.onclose = function (event) {
			websocket = null;
			console.log("WebSocket connection [closed]");
		};

		websocket.onerror = function (event) {
			websocket = null;
			console.log("WebSocket connection error");
			onMessage(event);
		};
	} else {
		console.warn("Warning: WebSocket already connected");
	}
};

export function close() {
	if (isConnected()) {
		websocket.close();
		websocket = null;
	}
};

/**
 * Expects messages in form of 
 * a WsoCommonMessage object - and sends it as JSON representation
 */
export function sendMessage(wsoMsg, sentCb = null) {
	if (wsoMsg instanceof WsoCommonMessage) {
		if (isConnected()) {
			let msg = createWsoMessageString(wsoMsg);
			websocket.send(msg);
			if (sentCb) { sentCb(); }
			return true;
		} else {
			console.warn("WebSocket NOT connected");
			WbApp.confirm({ message: "The Server Connection was closed.<br>Would you like to try a reconnect?" }, (value) => value ? connect() : null);
		}
	} else {
		throw new Error("WsoCommonMessage type expected");
	}
	return false;
};

export function addMessageListener(cb, subject = "any") {
	if (Object.hasOwn(listener, subject)) {
		if (!listener[subject].some(item => item === cb)) {
			listener[subject].push(cb);
		}
	} else {
		listener[subject] = [cb];
	}
};

/**
 * Expects messages in form of:
 * JSON representing a WsoCommonMessage
 */
function onMessage(event) {
	let subject = "any";
	let msg = "";
	let wsoMsg = new WsoCommonMessage("");

	if (event.type !== "error") {
		msg = JSON.parse(event.data);
		wsoMsg = Object.assign(wsoMsg, msg);
	} else {
		wsoMsg.setStatusError("connection error");
	}

	listener[subject].forEach((cb) => cb(wsoMsg));
};

function createWsoMessageString(wsoMsg){
	let msg = wsoMsg.reference.length > 0 ? "<"+wsoMsg.reference+">": "";
	msg = msg + JSON.stringify(wsoMsg);
	return msg;
}