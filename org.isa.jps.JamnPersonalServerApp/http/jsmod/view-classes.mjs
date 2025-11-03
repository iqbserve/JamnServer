/* Authored by iqbserve.de */

import { findChildOf, setVisibility, setDisplay, typeUtil, fileUtil, fetchPlainText } from '../jsmod/tools.mjs';
import { ViewSource } from '../jsmod/data-classes.mjs';
import { WorkbenchInterface as WbApp } from '../jsmod/workbench.mjs';
import { UIBuilder, onClicked, onDblClicked, DefaultCompProps, ContextId, newUIId, reworkHtmlElementIds } from '../jsmod/uibuilder.mjs';
import * as Icons from '../jsmod/icons.mjs';

/**
 * Internal section
 */
const InternalUIBuilder = new UIBuilder()
	.setElementCollection(null)
	.setObjectCollection(null);

/**
 * Get a view html file from the server
 */
function getViewHtml(viewSrc, cb) {
	if (viewSrc.isEmpty()) {
		//load the html from server
		fetchPlainText(viewSrc.getFile()).then((html) => {
			viewSrc.setHtml(html);
			cb(viewSrc.getHtml());
		});
	} else {
		cb(viewSrc.getHtml());
	}
}

/**
 * Create a standalone view dom element
 */
function createViewElementFor(view, html) {
	let template = document.createElement("template");
	template.innerHTML = html;
	view.viewElement = template.content.firstElementChild;
	if (view instanceof AbstractView) {
		view.viewElement.id = view.id;
	}
}

/**
 * Public section
 */

/**
 */
export function loadServerStyleSheet(path) {
	UIBuilder.loadServerStyleSheet(path);
}

/**
 * A basic view class. 
 */
export class AbstractView {

	//an automatic uid 
	uid = new ContextId();
	//a custom id
	id = "";
	viewSource = new ViewSource("");
	viewElement = null;
	//the obligatory flag to control the init sequence
	isInitialized = false;

	constructor(id, file = null) {
		this.id = id;
		this.viewSource = new ViewSource(file);
		this.isInitialized = false;
	}

	/**
	 * Get and lazy create the view dom element.
	 */
	getViewElement(cb = (elem) => { }) {
		if (this.needsInitialization()) {
			getViewHtml(this.viewSource, (html) => {
				html = this.reworkHtml(html);
				this.beforeCreateViewElement();
				createViewElementFor(this, html);
				this.initialize();
				cb(this.viewElement);
			});
		} else {
			cb(this.viewElement);
		}
	}

	/**
	 */
	needsInitialization() {
		//a basic, overwriteable initialization logic
		return !this.isInitialized || !this.viewElement;
	}

	/**
	 */
	reworkHtml(html) {
		//to be overwritten
		return html;
	}

	/**
	 * has to be overwritten when working with the uid
	 */
	getElement(id) {
		return findChildOf(this.viewElement, id);
	}

	/**
	 */
	beforeCreateViewElement() {
		//to be overwritten
	}

	/**
	 */
	initialize() {
		//to be overwritten
	}

	setVisible(flag) {
		setVisibility(this.viewElement, flag);
	}

	setDisplay(elem, flag) {
		setDisplay(elem, flag);
	}
}

/**
 * Work View base class.
 */
export class WorkView extends AbstractView {
	viewManager = null;

	viewHeader;
	viewBody;
	viewWorkarea;
	sidePanel;

	bodyInitialDisplay;

	state = {
		isRunning: false,
		isOpen: false,
		isPinned: false,
		isCollapsed: false
	}

	constructor(id, file) {
		super(id, file);

		this.state.isRunning = false;
		this.state.isOpen = false;
	}

	reworkHtml(html) {
		html = reworkHtmlElementIds(html, this.uid.get());
		return html;
	}

	//overwritten cause html id rework
	getElement(id) {
		return findChildOf(this.viewElement, this.uid.get(id));
	}

	initialize() {
		//to be overwritten
		//called from getViewElement(...)

		this.viewBody = this.getElement("work-view-body");
		this.viewWorkarea = this.getElement("work-view-workarea");
		this.bodyInitialDisplay = this.viewBody.style.display;

		this.viewHeader = new WorkViewHeader(this, this.state);
		this.viewHeader.rightIconBar((bar) => {
			bar.addIcon({ id: "close.icon", title: "Close view" }, Icons.close(), (evt) => {
				this.viewManager.onViewAction(evt, "close");
			});
			bar.addIcon({ id: "pin.icon", title: "Pin to keep view" }, Icons.pin(), (evt) => {
				this.togglePinned(evt);
			});
			bar.addIcon({ id: "collapse.icon", title: "Collapse view" }, Icons.collapse(), (evt) => {
				this.toggleCollapsed(evt);
			});
		});

		this.viewHeader.menu((menu) => {
			menu.addItem("Close", (evt) => {
				this.viewManager.onViewAction(evt, "close");
			}, { separator: "bottom" });

			if (this.viewManager) {
				menu.addItem("Move up", (evt) => {
					this.viewManager.moveView(this, "up");
				});
				menu.addItem("Move down", (evt) => {
					this.viewManager.moveView(this, "down");
				});
				menu.addItem("Move to ...", (evt) => {
					this.viewManager.promptUserInput({ title: "", message: "Please enter your desired position number:" }, "1",
						(value) => value ? this.viewManager.moveView(this, value) : null
					);
				});
			}
		});
	}

	open(data = null) {
		this.viewHeader.menu().close();
		this.state.isOpen = true;
	}

	close() {
		if (this.isInitialized) {
			this.state.isOpen = false;
			this.viewHeader.menu().close();
		}
		return this.isCloseable();
	}

	isCloseable(ctxObj = null) {
		return !(this.state.isRunning || this.state.isPinned);
	}

	setRunning(flag) {
		this.state.isRunning = flag;
		this.viewHeader.showRunning(flag);
	}

	setTitle(title) {
		this.viewHeader.setTitle(title);
	}

	onInstallation(installKey, installData, viewManager) {
		this.viewManager = viewManager;
		if (installKey && this.id.length == 0) {
			this.id = installKey;
		}
	}

	installSidePanel(sidePanelViewElem, workareaElem = this.viewWorkarea) {
		this.sidePanel = new WorkViewSidepanel(this, workareaElem);
		this.sidePanel.setViewComp(sidePanelViewElem);

		this.viewHeader.rightIconBar((bar) => {
			bar.addIcon({ id: "sidepanel.icon", title: "Show/Hide Sidepanel" }, Icons.wkvSidePanel(), (evt) => {
				this.toggleSidePanel(evt);
			});
		});

		return this.sidePanel;
	}

	toggleSidePanel(evt = null) {
		this.sidePanel.toggle();
		this.viewHeader.icons["sidepanel.icon"].toggle((icon) => {
			icon.title = this.sidePanel.isOpen() ? "Hide Sidepanel" : "Show Sidepanel";
		});
	}

	togglePinned(evt = null) {
		this.state.isPinned = !this.state.isPinned;

		this.viewHeader.icons["pin.icon"].toggle((icon) => {
			icon.title = this.state.isPinned ? "Unpin view" : "Pin to keep view";
			this.viewHeader.icons["close.icon"].setEnabled(!this.state.isPinned);
		});

		return this.state.isPinned;
	}

	toggleCollapsed(evt = null) {
		this.state.isCollapsed = !this.state.isCollapsed;

		this.viewHeader.container.classList.toggle("work-view-collapsed-header");
		this.viewHeader.icons["collapse.icon"].toggle((icon) => {
			icon.title = this.state.isCollapsed ? "Expand view" : "Collapse  view";
			let displayVal = !this.state.isCollapsed ? this.bodyInitialDisplay : "none";
			if (displayVal == "none" && this.sidePanel?.isOpen()) {
				this.toggleSidePanel();
			}
			setDisplay(this.viewBody, displayVal);
		});
		this.viewHeader.icons["sidepanel.icon"]?.setEnabled(!this.state.isCollapsed);
		return this.state.isCollapsed;
	}

	statusLineInfo(info) {
		WbApp.statusLineInfo(info);
	}

	copyToClipboard(text) {
		if (!this.state.isRunning && (text && text.length > 0)) {
			navigator.clipboard.writeText(text);
		}
	}

	saveToFile(fileName, text) {
		if (!this.state.isRunning && text.length > 0) {
			fileUtil.saveToFileClassic(fileName, text);
		}
	}

}

/**
 */
export class WorkViewHeader {
	view;
	viewState;

	container;
	icons = {};
	headerMenu;
	iconBarLeft;
	iconBarRight;
	progressBar;
	title;

	constructor(view, viewState) {
		this.view = view;
		this.viewState = viewState;
		this.#initialize();
	}

	#initialize() {
		this.container = this.#getElement("work-view-header");
		this.title = this.#getElement("view-title");
		this.headerMenu = new WorkViewHeaderMenu(this.#getElement("header-menu"));

		this.iconBarLeft = new WorkViewHeaderIconBar(this.#getElement("wkv-header-iconbar-left"), this.icons);
		this.iconBarLeft.addIcon({ id: "menu.icon", title: "View Menu" }, Icons.dotmenu(), (evt) => {
			this.#toggleHeaderMenu(evt);
		});
		this.iconBarRight = new WorkViewHeaderIconBar(this.#getElement("wkv-header-iconbar-right"), this.icons);
		this.progressBar = this.#getElement("wkv-header-progressbar");
	}

	#getElement(id) {
		return this.view.getElement(id);
	}

	#toggleHeaderMenu(evt = null) {
		if (!this.viewState.isCollapsed) {
			this.headerMenu.toggleVisibility(evt);
		}
	}

	leftIconBar(configCb = null) {
		if (configCb) {
			configCb(this.iconBarLeft);
		}
		return this.iconBarLeft;
	}

	rightIconBar(configCb = null) {
		if (configCb) {
			configCb(this.iconBarRight);
		}
		return this.iconBarRight;
	}

	menu(configCb = null) {
		if (configCb) {
			configCb(this.headerMenu);
		}
		return this.headerMenu;
	}

	showRunning(flag = null) {
		let classList = this.progressBar.firstElementChild.classList;
		let clazz = "progress-showWorking";
		classList.toggle(clazz);
		if (!this.viewState.isRunning && classList.contains(clazz)) {
			console.warn("isRunning flag mismatch");
		}
	}

	setTitle(text) {
		this.title.innerHTML = text;
	}
}

/**
 */
export class WorkViewSidepanel {
	view;
	splitHandler;
	splitterElem;
	sidePanelElem;
	workareaElem;
	viewCompElem;

	constructor(view, workareaElem) {
		this.view = view;
		this.workareaElem = workareaElem;
		this.#initialize();
	}

	#initialize() {

		this.splitterElem = this.view.getElement("work-view-sidepanel-splitter");
		this.sidePanelElem = this.view.getElement("work-view-sidepanel");

		if (this.splitterElem && this.sidePanelElem) {
			this.splitHandler = new SplitBarHandler(
				this.splitterElem,
				this.workareaElem,
				this.sidePanelElem
			);
			this.setWidth("100px");
		}
	}

	#isClosed() {
		let val = this.splitterElem.style.display;
		return (!val || val == "none");
	}

	isOpen() {
		return !this.#isClosed();
	}

	toggle() {
		if (this.#isClosed()) {
			this.open();
		} else {
			this.close();
		}
		return this;
	}

	open() {
		setDisplay(this.splitterElem, true);
		setDisplay(this.sidePanelElem, true);
		return this;
	}

	close() {
		setDisplay(this.splitterElem, false);
		setDisplay(this.sidePanelElem, false);
		return this;
	}

	setViewComp(compElem) {
		this.viewCompElem = compElem;
		this.sidePanelElem.append(this.viewCompElem);
		return this;
	}

	setWidth(width) {
		this.sidePanelElem.style.width = width;
		return this;
	}
}

/**
 */
export class WorkViewHeaderMenu {

	#menuElem;
	#toggleEvent;

	constructor(containerElem) {
		this.#menuElem = containerElem;

		window.addEventListener("click", (event) => {
			this.#onAnyCloseTriggerEvent(event)
		});
		window.addEventListener("scroll", (event) => {
			this.#onAnyCloseTriggerEvent(event)
		}, true);
	}

	#onAnyCloseTriggerEvent(event) {
		if (this.#toggleEvent !== event) {
			this.close();
		}
	}

	#positionMenu(evt) {
		let trigger = evt.currentTarget;
		const rect = trigger.getBoundingClientRect();
		this.#menuElem.style.top = `${window.scrollY + rect.top - 10}px`;
		this.#menuElem.style.left = `${window.scrollX + rect.right + 10}px`;
	}

	toggleVisibility(evt = null) {
		if (this.hasItems()) {
			this.#toggleEvent = evt;
			this.#positionMenu(evt);
			setDisplay(this.#menuElem, this.#menuElem.style.display === "none");
		}
	}

	close() {
		setDisplay(this.#menuElem, false);
	}

	hasItems() {
		return this.#menuElem?.children.length > 0;
	}

	addItem(text, cb, props = {}) {
		let item = document.createElement("a");
		item.href = "view: " + text;
		item.innerHTML = text;

		onClicked(item, (evt) => {
			//cause <a> links are used as menu items 
			//their default behavior must be suppressed
			evt.preventDefault();
			cb(evt);
		});

		if (props?.separator) {
			let clazz = props.separator === "top" ? "menu-separator-top" : "menu-separator-bottom";
			item.classList.add(clazz);
		}

		if (props?.pos) {
			this.#menuElem.insertAdjacentElement(props.pos, item);
		} else {
			this.#menuElem.appendChild(item);
		}
	}
}

/**
 */
export class WorkViewHeaderIconBar {
	iconBarComp;
	items;

	constructor(iconBarElem, items = {}) {
		this.items = items;
		this.iconBarComp = InternalUIBuilder.newUICompFor(iconBarElem);
	}

	addIcon(props, icon, action) {
		this.iconBarComp.addActionIcon({ "iconName": icon, "title": props.title }, (icon, iconElem) => {
			onClicked(icon, (evt) => { action(evt); });
			this.items[props.id] = iconElem;
		});
	}

	getIconElement(id) {
		return this.items[id];
	}
}

/**
 * dialog specific function to exchange the current view
 */
function exchangeDialogView(dlgElem, viewElem) {
	let currentView = dlgElem.firstElementChild;

	if (!currentView) {
		dlgElem.append(viewElem);
	} else if (currentView !== viewElem) {
		dlgElem.removeChild(currentView);
		dlgElem.append(viewElem);
	}
}

/**
 * View Dialog 
 */
export class ViewDialog extends AbstractView {

	static #dialogElem = document.getElementById("app-view-dialog");
	static default = { clazzes: [], attribProps: {}, styleProps: { "margin-top": "150px" } };

	constructor(file) {
		super("", file);
		this.createDialogContentContainer();
	}

	/**
	 * the dialog standard layout
	 */
	createDialogContentContainer() {
		let builder = new UIBuilder()
			//using this as target for ui builder var collection
			//any "varid" gets a property of this
			.setElementCollection(this)
			.setDefaultCompProps(new DefaultCompProps());

		builder.newUICompFor(this.dialog())
			.addDiv({ varid: "content", clazzes: "view-dialog-cartridge" }, (content) => {
				content
					.addDiv({ clazzes: "view-dialog-head-area" }, (headarea) => {
						headarea.addDiv({ varid: "header", clazzes: "view-dialog-header" }, (header) => {
							header
								.addSpan({ varid: "logoIcon", clazzes: ["dlg-header-item", "dlg-logo-icon"] })
								.addSpan({ varid: "title", clazzes: ["dlg-header-item", "dlg-title"] })
								.addActionIcon({ varid: "closeIcon", iconName: Icons.close(), title: "Close" }, (closeIcon) => {
									closeIcon.class(["dlg-header-item", "dlg-close-icon"]);
									onClicked(closeIcon.domElem, () => { this.close(); });
								})
						})
						headarea.addDiv({ varid: "progressBar", clazzes: "vdlg-header-progressbar" }, (progressbar) => {
							progressbar.addDiv({ clazzes: "header-progress-value" })
						});
					})
					.addDiv({ varid: "viewArea", clazzes: "view-dialog-view-area" })
					.addDiv({ varid: "commandArea" })
					.addDiv({ varid: "disableOverlay", clazzes: "view-dialog-disable-overlay" });
			});
	}

	showRunning(flag = null) {
		let classList = this.progressBar.firstElementChild.classList;
		let clazz = "progress-showWorking";
		if (flag && !classList.contains(clazz)) {
			classList.add(clazz);
		} else if (!flag) {
			classList.remove(clazz);
		}
	}

	setDisabled(flag, cursor = null) {
		this.setDisplay(this.disableOverlay, flag);
		if (cursor) { this.disableOverlay.style.cursor = cursor; }
	}

	dialog() {
		return ViewDialog.#dialogElem;
	}

	beforeOpen() {
		//to be overwritten
	}

	open(cb = null) {
		this.getViewElement(() => {
			exchangeDialogView(this.dialog(), this.content);
			this.beforeOpen();
			if (cb) {
				cb(this);
			}
			this.dialog().showModal();
		})
	}

	close() {
		this.dialog().close();
	}

	getElement(id) {
		return findChildOf(this.dialog(), id);
	}

	/**
	 * make a list of element ids to properties of this
	 */
	elementsToProperties(elementIdList) {
		let elem = null;
		for (const id of elementIdList) {
			elem = this.getElement(id);
			if (elem) {
				this[id] = elem;
			}
		}
	}

	initialize() {
		this.viewArea.append(this.viewElement);
		this.isInitialized = true;
	}

	setTitle(title) {
		this.title.innerHTML = title;
		return this;
	}

	setAction(id, action) {
		onClicked(this.getElement(id), action);
		return this;
	}
}

/**
 * Standardialogs e.g. confirmation/message/input
 */
export class StandardDialog {

	static #dialogElem = document.getElementById("app-standard-dialog");

	inputField;
	#dragHandler;

	constructor() {
		this.createUI();
		this.initialize();
	}

	/**
	 */
	createUI() {
		let builder = new UIBuilder()
			//using this as target for ui builder var collection
			.setElementCollection(this)
			.setDefaultCompProps(new DefaultCompProps());

		builder.newUICompFor(this.#dialog())
			.addDiv({ varid: "header", clazzes: "standard-dialog-header" }, (header) => {
				header
					.addSpan({ varid: "title" }, (title) => { title.style({ width: "100%" }) })
					.addActionIcon({ varid: "closeIcon", iconName: Icons.close(), title: "Cancel", clazzes: "std-dlg-close-icon" })
			})
			.addDiv({ varid: "contentArea", clazzes: "standard-dialog-content-area" }, (content) => { })
			.addDiv({ clazzes: "standard-dialog-command-area" }, (commands) => {
				commands
					.addButton({ varid: "pbOk", text: "Ok", clazzes: "std-dlg-button" })
					.addButton({ varid: "pbCancel", text: "Cancel", clazzes: "std-dlg-button" }, (cancel) => { cancel.domElem.autofocus = true; })
			});

		this.#dialog().style["margin-top"] = ViewDialog.default.styleProps["margin-top"];
	}

	#dialog() {
		return StandardDialog.#dialogElem;
	}

	#open() {
		this.#dialog().showModal();
		this.#dragHandler.setStartPosition(0, this.#dialog().style["margin-top"])
			.startWorking();
	}

	#close() {
		this.#dragHandler.stop();
		this.#dialog().close();
	}

	initialize() {
		this.#dragHandler = new DialogDragHandler(this.#dialog(), this.header);
		this.#dragHandler.setTriggerFilter((evt) => {
			if (evt.target === this.closeIcon) { return true; }
		});
		this.#dialog().classList.add("draggable-dialog");
		this.#dragHandler.enabled = true;
	}

	openConfirmation(text, cb) {
		this.#setupFor("confirm", text, null, cb);
		this.#open();
	}

	openInput(text, value, cb) {
		this.#setupFor("input", text, value, cb);
		this.#open();
		this.inputField.focus();
		this.inputField.select();
	}

	#setupFor(type, text, value, cb) {

		onClicked(this.pbOk, (evt) => {
			this.#close();
			cb(type === "input" ? this.inputField.value : true);
		});

		onClicked(this.pbCancel, (evt) => {
			this.#close();
			cb(null);
		});

		onClicked(this.closeIcon, (evt) => {
			this.#close();
			cb(null);
		});

		if (type === "confirm") {
			this.#setupForConfirm(text);
		} else if (type === "input") {
			this.#setupForInput(text, value);
		}
	}

	#setupForConfirm(text) {
		this.pbOk.innerHTML = "Yes";
		this.pbCancel.innerHTML = "No";
		let title = "Confirmation required";

		if (typeof text === 'string' || text instanceof String) {
			this.title.innerHTML = title;
			this.contentArea.innerHTML = `<p>${text}</p>`;
		} else {
			this.title.innerHTML = text.title ? text.title : title;
			this.contentArea.innerHTML = `<p>${text?.message}</p>`;
		}
	}

	#setupForInput(text, value) {
		this.pbOk.innerHTML = "Ok";
		this.pbCancel.innerHTML = "Cancel";
		let title = "Input";
		let inputId = "standard-dialog-input";

		if (!value) { value = "" };

		if (typeof text === 'string' || text instanceof String) {
			this.title.innerHTML = title;
			this.contentArea.innerHTML = `<p class="std-inputdlg-text">${text}</p> 
			<input type="text" id="${inputId}" class="std-dlg-textfield" value="${value}">`;
		} else {
			this.title.innerHTML = text.title ? text.title : title;
			this.contentArea.innerHTML = `<p class="std-inputdlg-text">${text?.message}</p>
			<input type="text" id="${inputId}" class="std-dlg-textfield" value="${value}">`;
		}

		this.inputField = findChildOf(this.#dialog(), inputId);
	}
}

/**
 */
export class WorkViewTableHandler {
	tableElem;
	tableBody;
	tableData = null;
	ascOrder = false;
	sortIcon;

	constructor(tableElem) {
		this.tableElem = tableElem;
		this.tableBody = this.tableElem.querySelector('tbody');

		let iconElem = this.getHeader(0).getElementsByTagName("i")[0];
		this.sortIcon = Icons.tableSort(iconElem);
	}

	getHeader(idx) {
		return this.tableElem.getElementsByTagName("th")[idx];
	}

	setData(tableData) {
		this.clearData();
		this.tableData = tableData;

		this.tableData.rows.forEach((rowData, rowKey) => {
			let row = document.createElement("tr");
			row.className = "wkv";

			rowData.forEach((colVal, colKey) => {
				let col = document.createElement("td");
				col.className = "wkv";
				col.innerHTML = colVal;
				col.value = colKey;
				onClicked(col, (evt) => { this.tableData.cellClick(rowKey, colKey, evt); });
				onDblClicked(col, (evt) => { this.tableData.cellDblClick(rowKey, colKey, evt); });
				row.appendChild(col);
			});

			this.tableBody.appendChild(row);
		});
	}

	clearData() {
		this.tableData = null;
		this.tableBody.replaceChildren();
	}

	sortByColumn(colIdx) {
		this.ascOrder = !this.ascOrder;
		let rows = Array.from(this.tableBody.querySelectorAll('tr'));

		rows.sort((rowA, rowB) => {
			let cellA = rowA.querySelectorAll('td')[colIdx].textContent.trim();
			let cellB = rowB.querySelectorAll('td')[colIdx].textContent.trim();

			return this.ascOrder ? cellA.localeCompare(cellB) : cellB.localeCompare(cellA);
		});

		this.tableBody.replaceChildren();
		this.tableBody.append(...rows);
	}

	toggleColSort(colIdx) {
		this.sortIcon.toggle();
	}

	filterRows(colIdx, filterText) {
		let rows = Array.from(this.tableBody.querySelectorAll('tr'));
		let filter = filterText.toLowerCase();

		rows.forEach((row) => {
			let cellVal = row.querySelectorAll('td')[colIdx].textContent;
			row.style.display = cellVal.toLowerCase().indexOf(filter) < 0 ? "none" : "";
		});
	}

	newCellInputField(props = { clazz: "wkv-tblcell-edit-tf", booleanValue: null, datalist: [] }) {
		let comp = document.createElement('span');
		let ctrl = document.createElement('input');

		ctrl.comp = comp;
		ctrl.type = "text";
		ctrl.classList.add(props.clazz ? props.clazz : "wkv-tblcell-edit-tf");
		comp.append(ctrl);

		if (props?.booleanValue != null) {
			ctrl.type = "checkbox";
			ctrl.checked = props.booleanValue;
			ctrl.style.width = "20px";
			onClicked(ctrl, (evt) => { ctrl.value = typeUtil.stringFromBoolean(ctrl.checked) });
		} else if (props.datalist?.length > 0) {
			let item = null;
			let dataElem = document.createElement("datalist");
			dataElem.id = newUIId();
			props.datalist.forEach(entry => {
				item = document.createElement("option");
				item.value = entry;
				dataElem.append(item);
			});
			ctrl.setAttribute("list", dataElem.id);
			comp.append(dataElem);
		}
		return ctrl;
	}
}

/**
 * Table data represented as 
 * - map of rows (key:row)
 *  - each row a map of columns (key:column)
 */
export class TableData {
	rows;
	cellClick;
	cellDblClick;

	constructor() {
		this.rows = new Map();
		this.cellClick = (rowKey, colKey, evt) => { };
		this.cellDblClick = (rowKey, colKey, evt) => { };
	}

	addRow(key, columns) {
		this.rows.set(key, columns);
	}
}

/*********************************************
 * UI Handler
 *********************************************/
/**
 */
export class SplitBarHandler {

	splitter;
	compBefore;
	compAfter;
	orientation = "v";
	moveSplitter = false;

	clickPoint;

	barrierActionBefore = (handler, value) => { };
	barrierActionAfter = (handler, value) => { };

	constructor(splitter, compbefore, compafter) {
		this.splitter = splitter;
		this.compBefore = compbefore;
		this.compAfter = compafter;

		this.splitter.onmousedown = (evt) => {
			this.#onDragStart(evt);
		}
	}

	#onDragStart(evt) {

		this.splitter.classList.toggle("vsplitter-working");

		this.clickPoint = {
			evt,
			offsetLeft: this.splitter.offsetLeft,
			offsetTop: this.splitter.offsetTop,
			beforeWidth: this.compBefore.offsetWidth,
			afterWidth: this.compAfter.offsetWidth
		};

		//avoid cursor flicker
		let cursor = window.getComputedStyle(this.splitter)["cursor"];
		this.compBefore.style.cursor = cursor;
		this.compAfter.style.cursor = cursor;

		document.onmousemove = (evt) => {
			this.#doDrag(evt);
		};

		document.onmouseup = () => {
			document.onmousemove = document.onmouseup = null;
			this.compBefore.style.cursor = "default";
			this.compAfter.style.cursor = "default";
			this.splitter.classList.toggle("vsplitter-working");
		}
	}

	#doDrag(evt) {
		let delta = {
			x: evt.clientX - this.clickPoint.evt.clientX,
			y: evt.clientY - this.clickPoint.evt.clientY
		};

		if (this.orientation === "v") {
			this.#doVDrag(delta, evt);
		}
	}

	#doVDrag(delta, evt) {
		delta.x = Math.min(Math.max(delta.x, -this.clickPoint.beforeWidth),
			this.clickPoint.afterWidth);

		let val = this.clickPoint.offsetLeft + delta.x;
		if (this.barrierActionBefore(this, val)) { return; }

		if (this.moveSplitter) {
			this.splitter.style.left = val + "px";
		}
		this.compBefore.style.width = (this.clickPoint.beforeWidth + delta.x) + "px";
		this.compAfter.style.width = (this.clickPoint.afterWidth - delta.x) + "px";
	}

	stop() {
		document.dispatchEvent(new Event("mouseup", { bubbles: true, cancelable: true }));
	}
}

/**
 */
export class DialogDragHandler {
	#dlg;
	#handleElem;
	#active = false;
	#start = { x: 0, y: 0 };
	#startPos = { left: 0, top: 0 };

	#triggerFilter = (evt) => { };
	enabled = false;

	constructor(dialog, handleElem) {
		this.#dlg = dialog;
		this.#handleElem = handleElem;
	}

	startWorking() {
		if (!this.enabled) { return this; }
		this.#handleElem.addEventListener('pointerdown', this.#onPointerDown);
		window.addEventListener('resize', this.#onWindowResize);
		return this;
	}

	stop() {
		if (!this.enabled) { return; }
		this.#stopDragging();
		this.#handleElem.removeEventListener('pointerdown', this.#onPointerDown);
		window.removeEventListener('resize', this.#onWindowResize);
	}

	#stopDragging() {
		if (!this.enabled) { return; }
		this.#active = false;
		try { this.#handleElem.releasePointerCapture(evt.pointerId); } catch (e) { }
		document.removeEventListener('pointermove', this.#onPointerMove);
		document.removeEventListener('pointerup', this.#onPointerUp);
		document.removeEventListener('pointercancel', this.#onPointerUp);
	}

	setStartPosition(leftVal, topVal) {
		leftVal = typeUtil.isString(leftVal) ? parseInt(leftVal, 10) : leftVal;
		topVal = typeUtil.isString(topVal) ? parseInt(topVal, 10) : topVal;

		//center by default
		let left = leftVal != 0 ? leftVal : Math.max(0, (window.innerWidth - this.#dlg.offsetWidth) / 2);
		let top = topVal != 0 ? topVal : Math.max(0, (window.innerHeight - this.#dlg.offsetHeight) / 2);

		this.#dlg.style.left = Math.round(left) + 'px';
		this.#dlg.style.top = Math.round(top) + 'px';
		return this;
	}

	setTriggerFilter(cb) {
		this.#triggerFilter = cb;
		return this;
	}

	#onPointerDown = (evt) => {
		if (this.#triggerFilter(evt)) return;
		this.#handleElem.setPointerCapture(evt.pointerId);
		this.#active = true;
		this.#start.x = evt.clientX;
		this.#start.y = evt.clientY;
		this.#startPos.left = parseFloat(this.#dlg.style.left) || 0;
		this.#startPos.top = parseFloat(this.#dlg.style.top) || 0;
		document.addEventListener('pointermove', this.#onPointerMove);
		document.addEventListener('pointerup', this.#onPointerUp);
		document.addEventListener('pointercancel', this.#onPointerUp);
	};

	#onPointerUp = (evt) => {
		this.#stopDragging();
	};

	#onPointerMove = (evt) => {
		if (!this.#active) return;
		evt.preventDefault();
		const dx = evt.clientX - this.#start.x;
		const dy = evt.clientY - this.#start.y;
		let newLeft = this.#startPos.left + dx;
		let newTop = this.#startPos.top + dy;

		const maxLeft = window.innerWidth - this.#dlg.offsetWidth;
		const maxTop = window.innerHeight - this.#dlg.offsetHeight;
		newLeft = Math.min(Math.max(0, newLeft), maxLeft);
		newTop = Math.min(Math.max(0, newTop), maxTop);

		this.#dlg.style.left = Math.round(newLeft) + 'px';
		this.#dlg.style.top = Math.round(newTop) + 'px';
	};

	#onWindowResize = (evt) => {
		let left = parseFloat(this.#dlg.style.left) || 0;
		let top = parseFloat(this.#dlg.style.top) || 0;
		const maxLeft = Math.max(0, window.innerWidth - this.#dlg.offsetWidth);
		const maxTop = Math.max(0, window.innerHeight - this.#dlg.offsetHeight);
		if (left > maxLeft) this.#dlg.style.left = Math.round(maxLeft) + 'px';
		if (top > maxTop) this.#dlg.style.top = Math.round(maxTop) + 'px';
	};
}

