/* Authored by iqbserve.de */

/**
 * A module to centralize font icon usage.
 * Experimental providing icons from different providers (boostrap/google)
 */

/**
 * PUBLIC
 */
export function newIcon(name, elem = null) {
	return new IconElement(name, iconClasses[name], elem);
}

export function getIconClasses(name, asCssString = false) {
	let clazzes = iconClasses[name][0];
	if (asCssString) {
		return clazzes.join(" ");
	}
	return clazzes;
}

/**
 * the icon constants are functions
 * if an element is provided
 *  - a new IconElement object is returned
 * else
 *  - the icon name is returned
 */
export const caretdown = newConstantFunction("caretdown");
export const caretup = newConstantFunction("caretup");
export const clipboardAdd = newConstantFunction("clipboardAdd");
export const close = newConstantFunction("close");
export const collapse = newConstantFunction("collapse");
export const command = newConstantFunction("command");
export const dashCollapse = newConstantFunction("dashCollapse");
export const dotmenu = newConstantFunction("dotmenu");
export const eraser = newConstantFunction("eraser");
export const eye = newConstantFunction("eye");
export const github = newConstantFunction("github");
export const info = newConstantFunction("info");
export const login = newConstantFunction("login");
export const loginAction = newConstantFunction("loginAction");
export const menu = newConstantFunction("menu");
export const minusRemove = newConstantFunction("minusRemove");
export const password = newConstantFunction("password");
export const pin = newConstantFunction("pin");
export const plusNew = newConstantFunction("plusNew");
export const toggleExpand = newConstantFunction("toggleExpand");
export const question = newConstantFunction("question");
export const redo = newConstantFunction("redo");
export const run = newConstantFunction("run");
export const save = newConstantFunction("save");
export const system = newConstantFunction("system");
export const tableSort = newConstantFunction("tableSort");
export const tools = newConstantFunction("tools");
export const trash = newConstantFunction("trash");
export const user = newConstantFunction("user");
export const wkvSidePanel = newConstantFunction("wkvSidePanel");
export const xRemove = newConstantFunction("xRemove");

//google material icons
export const gi_system = newConstantFunction("gi_system");
export const gi_toggleExpand = newConstantFunction("gi_toggleExpand");

/**
 * INTERNAL
 */
/**
 * if constant is accessed without element
 *  - return icon name
 */
function newConstantFunction(name) {
	return (elem = null) => {
		if (!elem) {
			return name;
		}
		return new IconElement(name, iconClasses[name], elem);
	};
}

/**
 */
class IconElement {
	name;
	elem;
	#clazzes;

	constructor(name, clazzes, elem = null) {
		this.name = name;
		this.#clazzes = clazzes;
		this.apply(elem);
	}

	#getShapeClass(idx = 0) {
		return this.#clazzes[1][idx];
	}

	toString() {
		return this.name;
	}

	apply(elem) {
		if (elem && !this.elem) {
			this.elem = elem;
			this.getIconClasses().forEach(clazz => {
				this.elem.classList.add(clazz);
			});
		}
		return this;
	}

	hasInitialShape(){
		return this.elem.classList.contains(this.#getShapeClass(0));
	}

	toggle(cb = null) {
		if (this.elem) {
			this.elem.classList.toggle(this.#getShapeClass(0));
			this.elem.classList.toggle(this.#getShapeClass(1));
		}
		if (cb) { cb(this.elem); }
	}

	setEnabled(flag){
		if(!flag){
			this.elem.style["pointer-events"] = "none";
		}else{
			this.elem.style["pointer-events"] = "all";
		}
	}

	getIconClasses() {
		return this.#clazzes[0];
	}

	init(cb = (obj) => { }) {
		cb(this);
		return this;
	}
}

/**
 * <name> = [[iconClasses], [shapeClasses]]
 * e.g. [["bi", "bi-person"], ["bi-person", "bi-person-check"]]
 */
function createClassDef(shapes, typeClasses=BI_TypeClasses) {
	return Object.freeze([[...typeClasses, shapes[0]], Object.freeze(shapes)]);
}

const BI_TypeClasses = Object.freeze(["bi"]);
const GI_TypeClasses = Object.freeze(["gi", "material-symbols-outlined"]);

const iconClasses = Object.freeze({
	caretdown: createClassDef(["bi-caret-down", ""]),
	caretup: createClassDef(["bi-caret-up", ""]),
	clipboardAdd: createClassDef(["bi-clipboard-plus", ""]),
	close: createClassDef(["bi-x-lg", ""]),
	collapse: createClassDef(["bi-chevron-bar-contract", "bi-chevron-bar-expand"]),
	command: createClassDef(["bi-command", ""]),
	dashCollapse: createClassDef(["bi-dash-square", ""]),
	dotmenu: createClassDef(["bi-three-dots-vertical", ""]),
	eraser: createClassDef(["bi-eraser", ""]),
	eye: createClassDef(["bi-eye", "bi-eye-slash"]),
	github: createClassDef(["bi-github", ""]),
	info: createClassDef(["bi-info-square", "bi-info-square-fill"]),
	login: createClassDef(["bi-person", "bi-person-check"]),
	loginAction: createClassDef(["bi-box-arrow-in-right", ""]),
	menu: createClassDef(["bi-list", ""]),
	minusRemove: createClassDef(["bi-dash-square", ""]),
	password: createClassDef(["bi-key", ""]),
	pin: createClassDef(["bi-pin", "bi-pin-angle"]),
	plusNew: createClassDef(["bi-plus-square", ""]),
	toggleExpand: createClassDef(["bi-plus-square", "bi-dash-square"]),
	question: createClassDef(["bi-question-square", "bi-question-square-fill"]),
	redo: createClassDef(["bi-arrow-counterclockwise", ""]),
	run: createClassDef(["bi-caret-right-square", ""]),
	save: createClassDef(["bi-floppy", ""]),
	system: createClassDef(["bi-laptop", ""]),
	tableSort: createClassDef(["bi-arrow-down", "bi-arrow-up"]),
	tools: createClassDef(["bi-tools", ""]),
	trash: createClassDef(["bi-trash", ""]),
	user: createClassDef(["bi-person", ""]),
	wkvSidePanel: createClassDef(["bi-arrow-bar-left", "bi-arrow-bar-right"]),
	xRemove: createClassDef(["bi-x-square", ""]),

	//google material icons
	gi_system: createClassDef(["gi-computer", ""], GI_TypeClasses),
	gi_toggleExpand: createClassDef(["gi-expand-all", "gi-collapse-all"], GI_TypeClasses)
});
