/* Authored by iqbserve.de */

import { NL, newSimpleId, fileUtil, asDurationString } from '../jsmod/tools.mjs';
import { WsoCommonMessage, CommandDef } from '../jsmod/data-classes.mjs';
import { WorkView } from '../jsmod/view-classes.mjs';
import { WorkbenchInterface as WbApp } from '../jsmod/workbench.mjs';
import * as Icons from '../jsmod/icons.mjs';
import { UIBuilder, onClicked, onInput, onKeydown, KEY} from '../jsmod/uibuilder.mjs';
import { html as WorkViewHtml } from '../jsmod/html-components/work-view.html.mjs';

/**
 * A general View class for commands.
 *  - using web socket communication to execute server side "commands"
 *  - using a builder to create a the ui
 * 
 * Commands are either server side JavaScripts or java classes.
 * 
 * The client side command definitions are located in:
 *  - sidebar-content.mjs
 * The web socket counterpart on the server-side is:
 *  - org.isa.jps.comp.DefaultWebSocketMessageProcessor
 * The server side scripts are located in subdir
 *  - "scripts" for JS / and "extensions" for java
 * 
 */
class CommandView extends WorkView {
	//websocket communication ref id
	wsoRefId;

	commandDef = new CommandDef();
	commandName = "";
	runTime;
	duration;
	attachments = new Map();
	namedArgs = { none: "" };

	//input element for file dialog
	fileInput = null;

	//member objects to collect ui elements and ui objects from the builder
	elem = {};
	uiobj = {};

	constructor(id) {
		super(id, null);
		this.viewSource.setHtml(WorkViewHtml);
	}

	onInstallation(installKey, installData, viewManager) {
		super.onInstallation(installKey, installData, viewManager);
		if (installData instanceof CommandDef) {
			this.commandDef = installData;
			this.commandName = this.commandDef.command + " " + this.commandDef.script;
		}
	}

	initialize() {
		super.initialize();
		this.setTitle(this.commandDef.title);

		//just demo data
		this.namedArgs = { help: "-h", testfile: "-file=test-data.json", cdata: '<![CDATA[ {"name":"HelloFunction", "args":["John Doe"]} ]]>' };

		this.viewHeader.menu((menu) => {
			menu.addItem("Clear Output", (evt) => {
				this.clearOutput();
			}, { separator: "top" });
			menu.addItem("Clear View", (evt) => {
				this.clearAll();
			});
		});

		this.fileInput = fileUtil.createFileInputElement("text/*, .json, .txt", (evt) => {
			let [file] = this.fileInput.files;
			this.fileInput.value = "";
			this.addAttachment(file);
		});

		this.createUI();
		this.createWsoConnection();

		this.isInitialized = true;
		this.setVisible(true);
	}

	/**
	 */
	createUI() {

		let builder = new UIBuilder()
			//set the objects to hold all control dom elements with a varid
			.setElementCollection(this.elem)
			// and other things like e.g. datalists or "data-bind" infos
			.setObjectCollection(this.uiobj)
			//set default styles
			.setCompPropDefaults((props) => {
				props.apply(["label"], { styleProps: { "width": "80px" } });
			});

		//create a fieldset as component container in the view workarea
		let compSet;
		builder.newUICompFor(this.viewWorkarea)
			.addFieldset((comp) => {
				comp.style({ "margin-top": "10px", "gap": "10px" });
				compSet = comp.getDomElem();
			});

		builder.newUIComp()
			.addLabelButton({ text: "Command:" },
				{ varid: "pbRun", iconName: Icons.run(), text: this.commandName, title: "Run command" }, (label, pbRun) => {
					onClicked(pbRun, () => { this.runCommand() });
				})
			.appendTo(compSet);

		//arguments choice + demo data
		let namedArgsList = Object.getOwnPropertyNames(this.namedArgs);
		let taTitle = (this.commandDef.options.args ? "Command arguments: -h for help" : "<no args>") + "\nStructured text like e.g. JSON must be wrapped in a <![CDATA[ structured text ]]> tag.";
		let taPlaceholder = this.commandDef.options.args ? " -h + Enter for help" : "<no args>";
		builder.newUIComp()
			.style({ "align-items": "flex-start" })
			.addLabelTextArea({ text: "Args:" }, { varid: "taArgs" }, (label, textarea) => {
				textarea
					.title(taTitle)
					.style({ "width": "400px", "min-width": "400px", "height": "45px", "min-height": "45px", "text-align": "left" })
					.attrib({ placeholder: taPlaceholder, disabled: !this.commandDef.options.args });
				onKeydown(textarea, (evt) => {
					if (this.commandDef.options.args) {
						if (KEY.isEnter(evt) && evt.currentTarget.value.trim() === "-h") {
							this.runCommand();
						}
					}
				});
			})
			.addColContainer((argBox) => {
				argBox.style({ "margin-left": "20px" });
				argBox.addTextField({ varid: "tfNamedArgs", datalist: namedArgsList }, (argText) => {
					argText
						.style({ "width": "200px" })
						.attrib({ title: "Name of the defined arguments", placeholder: "named args", "data-bind": "namedArgs" })
				})
					.addRowContainer((iconBar) => {
						iconBar.style({ gap: "20px", "align-self": "flex-end", "margin-top": "5px" })
							.addActionIcon({ varid: "icoDeleteNamedArgs", iconName: Icons.trash(), title: "Delete current named arg" })
							.add("span", (separator) => { separator.style({ height: "20px", "border-right": "1px solid var(--border-gray)" }) })
							.addActionIcon({ varid: "icoSaveNamedArgs", iconName: Icons.save(), title: "Save current named args" })
							.addActionIcon({ varid: "icoClearArgChoice", iconName: Icons.eraser(), title: "Clear args and choice" }, (elem) => {
								elem.style({ "margin-left": "20px", "margin-right": "5px" })
							});
					})
			})
			.appendTo(compSet);

		//attachment list
		builder.newUIComp()
			.style({ "align-items": "flex-start" })
			.addLabel({ text: "Attachments:", elemType: "label-text" })
			.addColContainer((attachBox) => {
				attachBox
					.addRowContainer((iconBar) => {
						iconBar.style({ gap: "20px", "align-self": "flex-start" })
							.addActionIcon({ varid: "icoRemoveAllAttachments", iconName: Icons.trash(), title: "Remove all Attachments" })
							.addActionIcon({ varid: "icoAddAttachment", iconName: Icons.plusNew(), title: "Add Attachment" });
					}).addList({ varid: "lstAttachments" }, (list) => {
						list.style({ "min-width": "385px", "min-height": "20px", "padding": "10px" })
					});
			})
			.appendTo(compSet);

		//a separator
		builder.newUIComp().addSeparator((elem) => { elem.style({ width: "100%" }) }).appendTo(compSet);

		//the output area
		builder.newUIComp()
			.style({ "align-items": "flex-start" })
			.addColContainer((iconBar) => {
				iconBar.style({ "align-items": "center", "gap": "15px" });
				iconBar
					.addLabel({ text: "Output:", name: "lbOutput" })
					.addActionIcon({ varid: "icoOutputSave", iconName: Icons.save(), title: "Save current output to a file" }, (icon) => {
						onClicked(icon, () => { this.saveOutput(); });
					})
					.addActionIcon({ varid: "icoOutputToClipboard", iconName: Icons.clipboardAdd(), title: "Copy current output to clipboard" }, (icon) => {
						onClicked(icon, () => { this.copyOutputToClipboard(); });
					})
					.addActionIcon({ varid: "icoOutputDelete", iconName: Icons.trash(), title: "Delete current output" }, (icon) => {
						onClicked(icon, () => { this.clearOutput(); });
					});
			})
			.addTextArea({ varid: "taOutput" }, (taOutput) => {
				taOutput.class("wkv-output-textarea-ctrl").style({ width: "626px", "min-width": "626px" }).attrib({ disabled: true })
					.linkToLabel("lbOutput");
			})
			.appendTo(compSet);

		onInput(this.elem.tfNamedArgs, (evt) => {
			let key = evt.currentTarget.value;
			this.setArgsSelection(key);
		});
		onClicked(this.elem.icoClearArgChoice, () => { this.clearArgChoice(); });
		onClicked(this.elem.icoDeleteNamedArgs, () => { this.deleteArgChoice(); });
		onClicked(this.elem.icoSaveNamedArgs, () => { this.saveArgChoice(); });
		onClicked(this.elem.icoAddAttachment, () => { this.fileInput.click(); });
		onClicked(this.elem.icoRemoveAllAttachments, () => { this.removeAllAttachments(); });
	}

	createWsoConnection() {
		this.wsoRefId = newSimpleId(this.id + ":");
		WbApp.addWsoMessageListener((wsoMsg) => {

			if (wsoMsg.hasReference(this.wsoRefId)) {
				if (wsoMsg.hasStatusSuccess()) {
					this.setRunning(false);
					this.addOutputLine(NL + `Command finished: [${wsoMsg.status}] [${this.commandName}] [${asDurationString(this.runTime)}]`);
				} else if (wsoMsg.hasStatusError()) {
					this.addOutputLine(NL + wsoMsg.error);
					this.setRunning(false);
				} else {
					this.addOutputLine(wsoMsg.bodydata);
				}
			} else if (wsoMsg.hasStatusError && wsoMsg.error.includes("connection")) {
				this.addOutputLine(NL + wsoMsg.error);
				this.setRunning(false);
			} else if (wsoMsg.hasStatusError && wsoMsg.hasReference("server.global")) {
				this.addOutputLine(NL + "WebSocket Error [" + wsoMsg.error + "] the central connection was closed.");
				this.setRunning(false);
			}
		});
	}

	clearAll() {
		this.clearArgChoice();
		this.clearOutput();
		this.removeAllAttachments();

		//resize elements
		this.elem.taArgs.style.width = "0px";
		this.elem.taArgs.style.height = "0px";
		this.elem.taOutput.style.width = "0px";
		this.elem.taOutput.style.height = "0px";
	}

	setRunning(flag) {
		super.setRunning(flag);
		this.elem.pbRun.disabled = flag;
		if (flag) {
			this.runTime = Date.now();
		} else {
			this.runTime = Date.now() - this.runTime;
		}
	}

	runCommand() {
		let wsoMsg = new WsoCommonMessage(this.wsoRefId);
		wsoMsg.command = this.commandDef.command;
		wsoMsg.functionModule = this.commandDef.script;
		wsoMsg.argsSrc = this.elem.taArgs.value.trim();

		if (this.attachments.size > 0) {
			this.attachments.forEach(function (value, key) {
				wsoMsg.addAttachment(value.name, value.data);
			})
		}

		this.clearOutput();
		WbApp.sendWsoMessage(wsoMsg, () => {
			this.setRunning(true);
		});
	}

	addOutputLine(line) {
		this.elem.taOutput.value += line + NL;
		this.elem.taOutput.scrollTop = this.elem.taOutput.scrollHeight;
	}

	setArgsSelection(key) {
		if (this.commandDef.options.args && this.namedArgs[key]) {
			this.elem.taArgs.value = this.namedArgs[key];
		}
	}

	clearArgChoice() {
		this.elem.taArgs.value = "";
		this.elem.tfNamedArgs.value = "";
	}

	getDataListObjFor(name) {
		return this.uiobj[this.elem[name].list.id];
	}

	saveArgChoice() {
		let key = this.elem.tfNamedArgs.value.trim();
		if (key != "") {
			this.namedArgs[key] = this.elem.taArgs.value.trim();
			let datalist = this.getDataListObjFor("tfNamedArgs");
			datalist.addOption(key);
		}
	}

	deleteArgChoice() {
		let key = this.elem.tfNamedArgs.value.trim();

		if (this.namedArgs[key]) {
			WbApp.confirm({
				message: `<b>Delete entry</b><br>Do you want to delete <b>[${key}]</b> from arg choice?`
			}, (val) => {
				if (val) {
					delete this.namedArgs[key];
					let dataListObj = this.getDataListObjFor("tfNamedArgs");
					dataListObj.removeOption(key);
					this.clearArgChoice();
				}
			});
		}
	}

	saveOutput() {
		let fileName = "output_" + (this.commandDef.command + "_" + this.commandDef.script).replaceAll("/", "_") + ".txt";
		this.saveToFile(fileName, this.elem.taOutput.value.trim());
	}

	copyOutputToClipboard() {
		this.copyToClipboard(this.elem.taOutput.value.trim());
	}

	clearOutput() {
		if (!this.state.isRunning) {
			let lastValue = this.elem.taOutput.value;
			this.elem.taOutput.value = "";
			return lastValue;
		}
		return "";
	}

	addAttachment(file) {
		if (file && !this.attachments.has(file.name)) {
			let attachment = new Attachment(file.name);
			let reader = new FileReader();
			reader.onload = (e) => {
				attachment.data = e.target.result;
			};
			reader.readAsText(file);

			this.attachments.set(file.name, attachment);
			this.addAttachmentToList(attachment);
		}
	}

	addAttachmentToList(attachment) {
		let item = document.createElement("li");
		item.classList.add("indexed");
		let iconClazzes = Icons.getIconClasses(Icons.xRemove(), true);
		let html = `<span class='${iconClazzes} wkv-listitem-ctrl' title='Remove Attachment' style='margin-right: 20px;'></span> <span>${attachment.name}</span>`;
		item.innerHTML = html;

		this.elem.lstAttachments.appendChild(item);
		//TODO firstChild ?
		onClicked(item.firstElementChild, (evt) => {
			let name = evt.target.parentElement.lastElementChild.textContent;
			this.removeAttachmentFromList(name, item);
		});
	}

	removeAttachmentFromList(name, item) {
		this.elem.lstAttachments.removeChild(item);
		this.attachments.delete(name);
	}

	removeAllAttachments() {
		this.attachments = new Map();
		let list = this.elem.lstAttachments;
		while (list.firstChild) list.removeChild(list.firstChild);
	}

}

class Attachment {
	name;
	data;
	constructor(name) {
		this.name = name;
	}
}

//export this view component as individual instances
//the view will get specified by a CommandDef data object
export function getView(id = "") {
	return new CommandView(id);
}

