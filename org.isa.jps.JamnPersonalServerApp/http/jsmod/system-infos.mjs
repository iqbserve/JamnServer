/* Authored by iqbserve.de */

import { callWebService, typeUtil } from '../jsmod/tools.mjs';
import { WorkView, WorkViewTableHandler, TableData } from '../jsmod/view-classes.mjs';
import { WorkbenchInterface as WbApp } from '../jsmod/workbench.mjs';
import * as webapi from '../jsmod/webapi.mjs';
import * as Icons from '../jsmod/icons.mjs';
import { UIBuilder, onClicked, onKeyup, KEY } from '../jsmod/uibuilder.mjs';
import { html as WorkViewHtml } from '../jsmod/html-components/work-view.html.mjs';

/**
 * Concrete view class for the info component
 */
class SystemInfoView extends WorkView {

	appBoxElem = {};

	configBoxElem = {};
	configTable;

	needsViewDataRefresh = true;

	constructor(id) {
		super(id, null);
		this.viewSource.setHtml(WorkViewHtml);
	}

	initialize() {
		super.initialize();
		this.setTitle("System Infos");

		let builder = new UIBuilder()
			.setCompPropDefaults((props) => {
				props.get("label").styleProps = { "min-width": "80px", "text-align": "right" };
			});

		this.boxWidth = "720px";
		this.app_scm_tab1 = "?tab=readme-ov-file#jamn---just-another-micro-node-server";

		this.#initWorkarea(builder);
		this.#initAppBox(builder);
		this.#initConfigBox(builder);

		this.isInitialized = true;
	}

	open() {
		super.open();
		getInfos((data) => {
			this.writeDataToView(data);
			this.setVisible(true);
		});
	}

	/**
	 */
	#initWorkarea(builder) {

		builder.setElementCollection(this);
		builder.newUICompFor(this.viewWorkarea)
			.style({ "display": "flex", "flex-direction": "row" })
			.addColContainer({ varid: "leftContainer" })
			.addColContainer({ varid: "rightContainer" }, (comp) => {
				comp.style({ width: "100%", "justify-content": "center", "align-items": "center", "margin-left": "40px", "margin-right": "20px" })
					.add("img", (image) => {
						image.attrib({ src: "images/intro.png", alt: "App Info", title: "Jamn Workbench" })
							.style({ width: "350px", border: "1px solid var(--border-gray)" });
					});
			});
	}

	/**
	 */
	#initAppBox(builder) {

		let compSet;
		builder.setElementCollection(this.appBoxElem);

		builder.newUICompFor(this.leftContainer)
			.addFieldset({ title: "Application" }, (fieldset) => {
				fieldset.style({ width: this.boxWidth });
				compSet = fieldset.getDomElem();
			});

		builder.newUIComp()
			.addLabelTextField({ text: "Name:" }, { varid: "tfName", readOnly: true }, (label, textField) => {
				textField.style({ "font-size": "18px", color: "var(--isa-title-grayblue)" });
			})
			.appendTo(compSet);

		builder.newUIComp()
			.addLabelTextField({ text: "Version:" }, { varid: "tfVersion", readOnly: true })
			.appendTo(compSet);

		builder.newUIComp()
			.style({ "align-items": "baseline", "padding-right": "5px", "margin-top": "20px" })
			.addLabelTextArea({ text: "Description:", name: "lbDescr" }, { varid: "tfDescription", rows: 3, readOnly: true })
			.appendTo(compSet);

		builder.newUIComp()
			.style({ "flex-direction": "row-reverse" })
			.addLink({ varid: "lnkReadMore", text: "Read more on GitHub ... " }, (link) => {
				link.style({ "text-align": "right" })
					.attrib({ title: "Jamn Personal Server - All-In-One MicroService App", target: "_blank" })
			})
			.appendTo(compSet);
	}

	/**
	 */
	#initConfigBox(builder) {

		let fieldset;
		builder.setElementCollection(this.configBoxElem);

		builder.newUICompFor(this.leftContainer)
			.addFieldset({ title: "Configuration" }, (comp) => {
				comp.style({ "padding-top": "10px", width: this.boxWidth });
				fieldset = comp;
			});

		fieldset.addRowContainer((comp) => {
			comp.style({ "flex-direction": "row-reverse", "margin-bottom": "10px", "gap": "15px" })
				.addActionIcon({ varid: "icoSave", iconName: Icons.save(), title: "Save current changes" }, (saveIcon) => {
					onClicked(saveIcon, () => {
						updateInfos(getUpdateRequest(), (response) => {
							if (response?.status === "ok") {
								clearConfigChanges()
								console.log("App-Info update done");
							}
						});
					});
				})
				.addActionIcon({ varid: "icoRedo", iconName: Icons.redo(), title: "Undo changes" }, (redoIcon) => {
					onClicked(redoIcon, () => {
						//open confirmation dialog
						WbApp.confirm({
							message: "<b>Undo all changes</b><br>Do you want to discard all changes?"
						}, (value) => value ? clearConfigChanges(true) : null);
					});
				});
		});

		fieldset.addFromHtml(this.reworkHtml(tableHtml), (elems) => {
			let tableElem = elems[0].firstElementChild;
			this.configTable = new WorkViewTableHandler(tableElem);
		});

		this.setActionsEnabled(false);
	}

	/**
	 */
	setActionsEnabled(flag) {
		let ctrls = [this.configBoxElem.icoSave, this.configBoxElem.icoRedo];
		let styleProps = flag ? { "pointer-events": "all", color: "" } : { "pointer-events": "none", color: "var(--border-gray)" };

		ctrls.forEach((ctrl) => UIBuilder.setStyleOf(ctrl, styleProps));
	}

	/**
	 */
	writeDataToView(data) {
		if (this.needsViewDataRefresh) {
			clearConfigChanges();

			this.appBoxElem.tfName.value = data.name;
			this.appBoxElem.tfVersion.value = `${data.version} - Build [${data.buildDate} UTC]`;
			this.appBoxElem.tfDescription.value = data.description;
			this.appBoxElem.lnkReadMore.href = data.links["app.scm"] + this.app_scm_tab1;

			//create+build a table data object
			let tableData = new TableData();
			// "data.config" has the structure: { name1:value1, name2:value2 ... }
			// create a 2 column tableData from it
			let names = Object.getOwnPropertyNames(data.config);
			names.forEach((name) => {
				let row = new Map();
				//mark the read only key column to filter out 
				row.set("key:" + name, name);
				row.set(name, data.config[name]);
				tableData.addRow(name, row);
			})

			//define cell editing on double click
			tableData.cellDblClick = (rowKey, colKey, evt) => {

				//editing only for the value column
				if (!colKey.startsWith("key:")) {
					//get the origin data from the data object (model)
					let dataRow = tableData.rows.get(rowKey);
					let dataValue = dataRow.get(colKey);
					console.log(dataValue);

					//create+handle a simple cell input field
					let cellElem = evt.currentTarget;
					if (cellElem.getElementsByTagName('input').length > 0) return;
					//for simplicity use the html table cell value
					let orgCellValue = cellElem.innerHTML;
					cellElem.innerHTML = '';

					let inputFieldProps = {};
					inputFieldProps.booleanValue = typeUtil.booleanFromString(orgCellValue);
					let cellInput = this.configTable.newCellInputField(inputFieldProps);
					cellInput.value = orgCellValue;

					cellInput.onblur = (evt) => {
						let newValue = cellInput.value;
						cellElem.removeChild(cellInput.comp);
						if (typeUtil.isBooleanString(newValue) && !typeUtil.isBooleanString(orgCellValue)) {
							newValue = orgCellValue;
						}
						cellElem.innerHTML = newValue !== orgCellValue ? newValue : orgCellValue;
						ckeckConfigChange(colKey, dataValue, cellElem);
					};

					cellInput.onkeydown = (evt) => {
						if (KEY.isEnter(evt)) {
							cellInput.blur();
						} else if (KEY.isEscape(evt)) {
							cellInput.blur();
							cellElem.innerHTML = orgCellValue;
							ckeckConfigChange(colKey, dataValue, cellElem);
						}
					};

					cellElem.appendChild(cellInput.comp);
					cellInput.focus();
				}
			};

			this.configTable.setData(tableData);
			this.configTable.sortByColumn(0);

			onClicked(this.configTable.getHeader(0).getElementsByTagName("i")[0], () => {
				this.configTable.sortByColumn(0);
				this.configTable.toggleColSort(0);
			});

			onKeyup(this.configTable.getHeader(0).getElementsByTagName("input")[0], (evt) => {
				this.configTable.filterRows(0, evt.target.value);
			});
			this.needsViewDataRefresh = false;
		}
	}
}

//export this view component as singleton instance
const viewInstance = new SystemInfoView("systemInfoView");
export function getView() {
	return viewInstance;
}
let configChanges = new Map();
let infoData = null;

/**
 */
export function getInfos(cb) {
	if (infoData) {
		cb(infoData);
	} else {
		//load the infos from server
		callWebService(webapi.system_getinfos).then((data) => {
			infoData = data;
			cb(infoData);
		});
	}
}

/**
 */
function updateInfos(request, cb) {
	//send changes to the server
	callWebService(webapi.system_updateinfos, JSON.stringify(request)).then((response) => {
		cb(response);
	});
}

/**
 */
function clearConfigChanges(undo = false) {
	configChanges.forEach((cell, key) => {
		cell.elem.style["border-left"] = "";
		if (undo) { cell.elem.innerHTML = cell.orgData; };
	});
	configChanges.clear();
	getView().setActionsEnabled(false);
}

/**
 */
function ckeckConfigChange(key, orgVal, cellElem) {
	let currentVal = cellElem.innerHTML;

	if (orgVal !== currentVal) {
		configChanges.set(key, { elem: cellElem, orgData: orgVal });
		cellElem.style["border-left"] = "3px solid #32cd32";
	} else {
		configChanges.delete(key);
		cellElem.style["border-left"] = "";
	}
	getView().setActionsEnabled(configChanges.size !== 0);
}

/**
 */
function getUpdateRequest() {
	let request = { "configChanges": {} };
	configChanges.forEach((cell, key) => {
		request.configChanges[key] = cell.elem.innerHTML;
	});
	return request;
}


let tableHtml = `
<div class="wkv-fix-tblhead-container">
	<table class="wkv" style="table-layout:fixed;">
		<thead>
			<tr>
				<th class="wkv" style="width: 250px;">
					<span style="display: flex; align-items: center;">
						<span>Key:</span>
						<input type="text" id="config.filter.tf" placeholder="Filter ..."
							class="embedded-search-field" style="min-width: 60%;">
						<span style="width: 100%;"></span>
						<i class="wkv-tblheader-ctrl" title="Sort"></i>
					</span>
				</th>
				<th class="wkv">Value:</th>
			</tr>
		</thead>
		<tbody></tbody>
	</table>
</div>
`