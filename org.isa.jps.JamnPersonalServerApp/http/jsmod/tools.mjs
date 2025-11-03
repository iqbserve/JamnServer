/* Authored by iqbserve.de */

import { WbProperties } from '../jsmod/workbench-properties.mjs';

/**
 * Some simple helper constants and functions
 */

export const NL = "\n";

/**
 * Object to define functions
 * that get lazily resolved at the first call
 */
export class LazyFunction {
	#moduleName;
	#functionName;
	#functionArgs;
	#returnOnly = false;

	constructor(module, fncName, fncArgs = null) {
		this.#moduleName = module;
		this.#functionName = fncName;
		this.#functionArgs = fncArgs;
	}

	asAction() {
		//do not call the function
		//just return it
		this.#returnOnly = true;
		return this;
	}

	call(cb) {
		import(this.#moduleName)
			.then((module) => {
				let retVal;
				if (this.#returnOnly) {
					retVal = module[this.#functionName]
				} else if(this.#functionArgs){
					retVal = module[this.#functionName](this.#functionArgs);
				}else{
					retVal = module[this.#functionName]();
				}
				cb(retVal);
			});
	}
}

/**
 */
export function BackendServerUrl(...path) {
	let url = WbProperties.getOrDefault("webBackendServerUrl", window.location.origin);

	let urlPath = path.join("/");
	if (urlPath.startsWith("/")) {
		url = url + urlPath;
	} else {
		url = url + "/" + urlPath;
	}
	return url;
}

/**
 */
export function findChildOf(root, childId) {
	const selector = `#${CSS.escape(childId)}`;
	return root.querySelector(selector);
}

/**
 */
export function setVisibility(elem, flag) {
	elem.style["visibility"] = flag ? "visible" : "hidden";
	return elem;
}

/**
 */
export function setDisplay(elem, flag) {
	if (typeof flag == "boolean") {
		elem.style["display"] = flag ? "block" : "none";
	} else if (typeof flag == "string") {
		elem.style["display"] = flag;
	}
	return elem;
}

/**
 */
export async function fetchPlainText(path) {
	const url = BackendServerUrl(path);
	let data = "";

	const response = await fetch(url, {
		method: "GET",
		accept: "text/plain",
		headers: { "Content-Type": "text/plain" },
		mode: "cors"
	})

	data = await response.text();

	return data;
}

/**
 */
export async function callWebService(path, requestData = "{}") {
	const url = BackendServerUrl(path);
	let data = "";

	const response = await fetch(url, {
		method: "POST",
		accept: "application/json",
		headers: { "Content-Type": "application/json" },
		mode: "cors",
		body: requestData
	})

	data = await response.text();
	data = JSON.parse(data);
	return data;
}

/**
 */
export function newSimpleId(prfx = "") {
	return prfx + Math.random().toString(16).slice(2);
}

/**
 */
export function mergeArrayInto(target, source, allowDuplicates = false) {
	target = target || [];
	source = source || [];
	//copy target
	target = [...target];
	source.forEach(value => {
		if (!target.includes(value) || allowDuplicates) {
			target.push(value);
		}
	});
	return target;
}

export function clearArray(array) {
	if (array) { array.length = 0; }
}

/**
 */
export const fileUtil = {

	/**
	 */
	saveToFileFapi: (fileName, text) => {
		window.showSaveFilePicker({
			suggestedName: fileName,
			types: [{
				description: "Text file",
				accept: { "text/plain": [".txt"] },
			}],
		}).then(async handler => {
			let file = await handler.createWritable();
			await file.write(text);
			await file.close();
		}).catch(err => console.error(err));
	},

	/**
	 */
	saveToFileClassic: (fileName, text) => {
		let blob = new Blob([text], { type: "text/plain" });
		let url = URL.createObjectURL(blob);

		let a = document.createElement("a");
		a.href = url;
		a.download = fileName;
		a.style.display = "none";
		a.click();
		URL.revokeObjectURL(url);
	},

	/**
	 */
	createFileInputElement: (fileTypes, cb) => {
		let fileInput = document.createElement("input");
		fileInput.type = "file";
		fileInput.style.display = "none";
		fileInput.accept = fileTypes;
		fileInput.addEventListener("change", cb);
		return fileInput;
	}

}

/**
 */
export const typeUtil = {

	isString: (val) => {
		return (typeof val === 'string' || val instanceof String);
	},

	isObject: (val) => {
		return (val !== null && typeof val === 'object');
	},

	isDomElement: (val) => {
		return (val !== null && (val instanceof Element || val.nodeType !== undefined));
	},

	isArray: (val) => {
		return Array.isArray(val);
	},

	isFunction: (val) => {
		return (val !== null && (typeof val === 'function' || val instanceof Function));
	},

	isNumber: (val) => {
		return (val !== null && typeof val === 'number');
	},

	isBoolean: (val) => {
		return (val === true || val === false);
	},

	isBooleanString: (val) => {
		return (val === "true" || val === "false");
	},

	booleanFromString: (val) => {
		if (typeUtil.isBooleanString(val)) {
			return (val === "true");
		}
		return null;
	},

	stringFromBoolean: (val) => {
		if (typeUtil.isBoolean(val)) {
			return val ? "true" : "false";
		}
		return null;
	}

}

/**
 */
export function asDurationString(ms) {
	const hours = String(Math.floor(ms / 3600000)).padStart(2, '0');
	const minutes = String(Math.floor((ms % 3600000) / 60000)).padStart(2, '0');
	const seconds = String(Math.floor((ms % 60000) / 1000)).padStart(2, '0');
	const milliseconds = String(ms % 1000).padStart(3, '0');

	return `${hours}:${minutes}:${seconds}:${milliseconds}`;
}
