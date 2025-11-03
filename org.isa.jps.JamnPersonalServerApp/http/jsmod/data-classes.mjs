/* Authored by iqbserve.de */

/**
 * Some simple data classes.
 */


/**
 * A common websocket message.
 */
export class WsoCommonMessage {

	//header data
	reference = "";
	command = "";
	functionModule = "";
	argsSrc = "";
	status = "";
	error = "";
	//payload
	bodydata = "";
	attachments = {};

	constructor(reference) {
		this.reference = reference;
	}

	hasReference(id) {
		return this.reference === id;
	}

	hasStatusSuccess() {
		return "success" === this.status.toLowerCase();
	}

	hasStatusError() {
		return "error" === this.status.toLowerCase();
	}

	setStatusError(errorInfo) {
		this.status = "error";
		this.error = errorInfo;
	}

	addAttachment(key, value) {
		this.attachments[key] = value;
	}
};

/**
 * A common command definition.
 */
export class CommandDef {
	title = "";
	command = "";
	script = "";
	options = { args: false }

	constructor(title, command, script, opt = { args: false }) {
		this.title = title;
		this.command = command;
		this.script = script;
		this.options = opt;
	}
};

/**
 * View definition struct.
 */
export class ViewSource {
	#file = "";
	#html = null;
	#htmlLoadListener = (viewSrc)=>{};

	constructor(file) {
		this.#file = file;
	}

	setHtmlLoadListener(cb){
		this.#htmlLoadListener = cb;
	}

	isEmpty() {
		return this.#html == null;
	}

	setHtml(html){
		this.#html = html;
		this.#htmlLoadListener(this);
	}

	getHtml(){
		return this.#html;
	}

	getFile(){
		return this.#file;
	}

};