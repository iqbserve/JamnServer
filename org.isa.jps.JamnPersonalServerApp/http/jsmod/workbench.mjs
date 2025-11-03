/* Authored by iqbserve.de */

import { BackendServerUrl, setDisplay, setVisibility } from '../jsmod/tools.mjs';
import { WorkbenchViewManager } from '../jsmod/view-manager.mjs';
import { SplitBarHandler } from '../jsmod/view-classes.mjs';
import * as websocket from '../jsmod/websocket.mjs';
import { getComponent as getSidebar } from '../jsmod/sidebar.mjs';
import * as sidebarContent from '../jsmod/sidebar-content.mjs';
import * as systemInfos from '../jsmod/system-infos.mjs';
import * as Icons from '../jsmod/icons.mjs';
import { WbProperties } from '../jsmod/workbench-properties.mjs';
import { UIBuilder, DefaultCompProps, onClicked } from '../jsmod/uibuilder.mjs';


/**
 * The workbench module implements the toplevel Single-Page-Application.
 */

/**
 * Browser-Document registration function
 */
export function anchorAt(rootId) {
	rootElement = document.getElementById(rootId);

	return {
		registerAs: function (globalName) {
			if (!(globalName in window)) {
				window[globalName] = WorkbenchInterface;
				console.log("Workbench App globally registered as [" + globalName + "]");
			} else {
				throw new Error("The global name [" + globalName + "] is already defined");
			}
			return this;
		},

		build: function () {
			document.addEventListener("DOMContentLoaded", startApp);
			console.log("Workbench App installed");
		}
	};
};

/**
 * A public NONE module function interface export
 * for use on document level -> see registerAs -> globalName  
 */
export const WorkbenchInterface = {

	confirm: (text, cb) => {
		viewManager.promptConfirmation(text, cb);
	},

	//public view action request
	onViewAction: (evt, action) => {
		viewManager.onViewAction(evt, action);
	},

	sendWsoMessage: (wsoMsg, sentCb = null) => {
		return websocket.sendMessage(wsoMsg, sentCb);
	},

	addWsoMessageListener: (cb) => {
		websocket.addMessageListener(cb);
	},

	statusLineInfo: (info) => {
		statusline.setInfoText(info);
	},

	titleInfo: (info) => {
		titlebar.setTitleText(info);
	}
};

/**
 * Internals
 */
let rootElement = null;
let systemData = null;
let viewManager = null;

let titlebar = null;
let statusline = null;

/**
 * this is called after document load but before getting visible
 */
function startApp() {

	systemInfos.getInfos((data) => {
		systemData = data;

		viewManager = new WorkbenchViewManager(document.getElementById("app-workarea"));

		initWebSocket();
		initUI();

		setVisibility(rootElement, true);

		if (WbProperties.autoStartView) {
			getSidebar().clickItem(WbProperties.autoStartView);
		}
	});
}

/**
 */
function initWebSocket() {
	let wsodata = {};
	wsodata.hostUrl = BackendServerUrl(WbProperties.getOrDefault("webSocketUrlRoot", "wsoapi"));

	websocket.initialize(wsodata);
	websocket.connect();
}

/**
 */
function initUI() {
	let wbDefaults = new DefaultCompProps();
	wbDefaults.get("actionIcon").clazzes = ["wkv-action-icon"];

	titlebar = new Titlebar(viewManager, document.getElementById("app-titlebar"))
		.setBuilderDefaults(wbDefaults)
		.build();

	statusline = new Statusline(systemData, document.getElementById("app-statusline"))
		.setBuilderDefaults(wbDefaults)
		.build();

	initSidebar();
	initIntroBox();

	WorkbenchInterface.titleInfo(`Tiny Demo - V.${systemData.version}`);
}

/**
 */
function initSidebar() {

	let sidebar = getSidebar(document.getElementById("app-sidebar"));
	sidebar.initializeWith(viewManager, sidebarContent);

	//init splitter
	let splitter = new SplitBarHandler(
		document.getElementById("app-sidebar-splitter"),
		document.getElementById("app-sidebar"),
		document.getElementById("app-workarea")
	)
	splitter.barrierActionBefore = (splitter, val) => {
		//sidebar width < x - collaps it
		if (val < 100) {
			splitter.stop();
			sidebar.toggleCollapse();
			return true; //barrier hit
		}
		return false; //barrier NOT hit
	}
}

/**
 */
function initIntroBox() {

	let intro = document.getElementById("app-intro-overlay");

	if (!WbProperties.showIntro) {
		setDisplay(intro, false);
		return;
	};

	onClicked(intro, (evt) => {
		setDisplay(evt.currentTarget, false);
	});

	let data = systemData.buildInfos;

	document.getElementById("app-intro-content").innerHTML = `
		<span style="padding: 20px;">
			<h1 style="color: var(--isa-title-grayblue)">Welcome to<br>Jamn Workbench</h1>
			<span style="font-size: 18px;">
			<p>an example of using the Jamn Java-SE Microservice<br>together with plain Html and JavaScript<br></p>
			<p style="margin-bottom: 5px;">to build lightweight, browser enabled
				<a class="${Icons.getIconClasses("github", true)}" style="color: var(--isa-title-blue);" title="Jamn All-In-One MicroService"
				target="_blank" href="${data["readme.url"]}"><span style="margin-left: 5px;">All-in-One Apps</span></a>
			</p>
			<a style="font-size: 10px; color: var(--isa-title-blue);" 
			href="${data["author.url"]}" title="${data["author"]}" target="_blank">${data["author"]}</a>
			</span>
		</span>
		<!---->
		<span>
			<img src="images/intro.png" alt="Intro" style="width: 100%; height: 100%;">
		</span>
	`;
}

/**
 */
class Titlebar {
	#builder;
	#viewMngr;
	#titlebarElem;
	#elem = {};

	constructor(viewMngr, anchorElement) {
		this.#viewMngr = viewMngr;
		this.#titlebarElem = anchorElement;

		this.#builder = new UIBuilder().setElementCollection(this.#elem);
	}

	#createUI() {
		this.#builder.newUICompFor(this.#titlebarElem)
			.style({ "user-select": "none" })
			.add("a", (logoIcon) => {
				logoIcon.attrib({ href: "https://iqbserve.de/", target: "_blank" }).style({ "min-width": "fit-content" })
					.add("img", (logoIconImg) => {
						logoIconImg.class("wtb-item")
							.attrib({ src: "images/workbench-logo.png", title: "IQB Services", alt: "logo" })
							.style({ width: "22px", height: "22px" });
					})
			})
			.addContainer((elem) => {
				elem.html("Jamn Workbench -").class("wtb-item").style({ "min-width": "fit-content" });
			})
			.addContainer({ varid: "titleText" }, (elem) => {
				elem.html("[ ]").class("wtb-item").style({ width: "100%", "user-select": "text" });
			})
			.addContainer((titleIconBar) => {
				titleIconBar.class(["wtb-item", "wtb-ctrl-panel"])
					.addActionIcon({ iconName: Icons.caretup() }, (icon) => {
						icon.title("Backward step through views");
						onClicked(icon, () => { this.#viewMngr.stepViewsUp(); });
					})
					.addActionIcon({ iconName: Icons.caretdown() }, (icon) => {
						icon.title("Forward step through views");
						onClicked(icon, () => { this.#viewMngr.stepViewsDown(); });
					});
			});
	}

	build() {
		this.#createUI();
		return this;
	}

	setBuilderDefaults(defaultProps) {
		this.#builder.setDefaultCompProps(defaultProps);
		return this;
	}

	setTitleText(text) {
		this.#elem.titleText.innerHTML = `[ ${text} ]`;
	}
}

/**
 */
class Statusline {
	#builder;
	#sysData;
	#statuslineElem;
	#elem = {};

	constructor(sysData, anchorElement) {
		this.#sysData = sysData;
		this.#statuslineElem = anchorElement;

		this.#builder = new UIBuilder().setElementCollection(this.#elem);
	}

	#createUI() {
		this.#builder.newUICompFor(this.#statuslineElem)
			.style({ "user-select": "none" })
			.addContainer((prefixText) => {
				prefixText.html("Info:").class("wsl-item").style({ width: "30px" });
			})
			.addContainer({ varid: "infoText" }, (infoText) => {
				infoText.class("wsl-item").style({ width: "100%", "user-select": "text" });
			})
			.addContainer((iconBar) => {
				iconBar.class("wsl-item").style({ width: "50px", "margin-right": "5px", "text-align": "center" })
					.add("a", (gitLink) => {
						gitLink.class(Icons.getIconClasses(Icons.github()))
							.attrib({ title: "Git repo", href: this.#sysData.links["app.scm"], target: "_blank" });
					});
			});
	}

	build() {
		this.#createUI();
		return this;
	}

	setBuilderDefaults(defaultProps) {
		this.#builder.setDefaultCompProps(defaultProps);
		return this;
	}

	setInfoText(text) {
		this.#elem.infoText.innerHTML = text;
	}
}