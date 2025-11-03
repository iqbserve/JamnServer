/* Authored by iqbserve.de */

import { LazyFunction } from '../jsmod/tools.mjs';
import * as Icons from '../jsmod/icons.mjs';
import { UIBuilder, DefaultCompProps, onClicked, onKeyup } from '../jsmod/uibuilder.mjs';

class Sidebar {
	sidebarElem;
	elem = {};

	constructor(anchorElement) {
		this.sidebarElem = anchorElement;
		this.#createUI();
	}

	#createUI() {
		let builder = new UIBuilder()
			.setElementCollection(this.elem)
			.setDefaultCompProps(new DefaultCompProps());

		builder.newUICompFor(this.sidebarElem)
			.addContainer({ varid: "header", clazzes: "sidebar-header" }, (header) => {
				header.addActionIcon({ varid: "menuIcon", iconName: Icons.menu() }, (menuIcon) => {
					menuIcon.title("Show/Hide sidebar menu").class("sidebar-header-icon");
					onClicked(menuIcon, (evt) => {
						this.toggleCollapse();
					})
				})
					.addContainer({ varid: "workIconBar", clazzes: "sidebar-header-workicons" });
			})
			.addContainer({ varid: "body", clazzes: "sidebar-body" }, (body) => {
				body.addContainer({ varid: "topicHead", clazzes: "sbar-topic-head" }, (topicHead) => {
					topicHead.addActionIcon({ iconName: Icons.gi_toggleExpand() }, (expandToggle, expandIconElem) => {
						expandToggle.title("Expand/Collapse Topics").class("sidebar-header-icon").style({ "font-size": "14px", "margin-left": "10px" });
						onClicked(expandToggle, (evt) => {
							this.#expandTopics(expandIconElem);
						});
					})
						.addTextField({ clazzes: "embedded-search-field" }, (searchField) => {
							searchField.style({ width: "50%", "max-width": "150px" }).attrib({ placeholder: "Filter ..." });
							onKeyup(searchField, (evt) => {
								this.#filterItems(evt.target.value);
							})
						});
				})
			})
			.addList({ varid: "topicList", clazzes: "sbar-topic-list" });

	}

	#newWorkIcon(id, iconName) {
		let elem = UIBuilder.createDomElementFrom(`<i id=${id} class="sidebar-header-icon"></i>`);
		let icon = Icons.newIcon(iconName, elem);
		return icon;
	}

	#createTopicList(viewManager, topicListDef) {
		let topicListElem = this.elem.topicList;
		let topicKey = null;
		let topicDef = null;
		let topicElem = null;
		let itemListElem = null;

		for (topicKey in topicListDef) {
			topicDef = topicListDef[topicKey];
			topicElem = this.#newTopic(topicKey, topicDef);
			itemListElem = this.#newTopicList();
			topicElem.append(itemListElem);
			topicListElem.append(topicElem);

			for (let key in topicDef.items) {
				let item = { resolved: false };
				item.key = topicKey + "_" + key;
				item.def = topicDef.items[key];
				item.elem = this.#newTopicItem(item.def.id, item.def.title);
				itemListElem.append(item.elem);
				if (item.def.view) {
					onClicked(item.elem, (evt) => {
						this.#callItemFunction(item.def.view, (viewObj) => {
							if (!item.resolved) {
								viewObj.onInstallation(item.key, item.def.data, viewManager);
								//must be set after onInstallation because view id might change
								item.elem.dataset.viewId = viewObj.id;
								viewManager.registerView(viewObj, item.def.data);
								item.resolved = true;
							}
							viewManager.onComponentOpenViewRequest(item.elem.dataset.viewId);
						});
					});

				} else if (item.def.action) {
					onClicked(item.elem, (evt) => {
						this.#callItemFunction(item.def.action, (actionObj) => {
							if (!item.resolved) {
								//custom property for direct actions 
								item.elem.action = actionObj;
								item.resolved = true;
							}
							item.elem.action();
						});
					});
				}
			}
		}
	}

	#callItemFunction(obj, cb) {
		//standard expectation
		if (obj instanceof LazyFunction) {
			//does the lazy function call 
			//and then calls the provided cb with the result
			obj.call(cb)
		} else {
			//if any other obj is provided
			//just call the cb with it
			cb(obj);
		}
	}

	#newTopic(key, def) {

		let iconClazzes = Icons.getIconClasses(def.iconName, true);
		let text = def.title;
		let html = `
		<li class="sbar-topic">
			<span class="sbar-topic-header node-trigger">
				<span class="sbar-topic-icon ${iconClazzes} node-trigger"></span>
				<span class="sbar-topic-text node-trigger">${text}</span>
			</span>
		</li>`;
		let elem = UIBuilder.createDomElementFrom(html);

		onClicked(elem, (evt) => {
			//prevent collapsing topic
			if (evt.target.classList.contains("node-trigger")) {
				let list = evt.currentTarget.lastChild;
				if (list) {
					if (list.style.display == "none" || list.style.display == "") {
						list.style.display = "block";
					} else {
						list.style.display = "none";
					}
				}
			}
		});

		return elem;
	}

	#newTopicList() {
		let html = `<ul class="sbar-item-list"></ul>`;
		let list = UIBuilder.createDomElementFrom(html);
		return list;
	}

	#newTopicItem(id, text) {
		id = id ? "id=" + id : "";
		let html = `<li class="sbar-item" ${id}>${text}</li>`;
		return UIBuilder.createDomElementFrom(html);
	}

	#expandTopics(iconElem) {
		let flag = iconElem.hasInitialShape();
		iconElem.toggle();
		iconElem.elem.title = !flag ? "Expand Topics" : "Collapse Topics";

		let displayVal = flag ? "block" : "none";

		let topics = this.sidebarElem.querySelectorAll("ul.sbar-item-list")
		for (const list of topics) {
			list.style.display = displayVal;
		}
	}

	#filterItems(text = "") {
		let filter = text.trim().toLowerCase();
		let itemText = "";
		let items = this.sidebarElem.querySelectorAll("li.sbar-item")
		let hasFilter = filter.length > 0;

		if (hasFilter) {
			this.elem.topicHead.classList.add("topic-head-freez");
		} else {
			this.elem.topicHead.classList.remove("topic-head-freez");
		}

		let showItem = (flag, item) => {
			let topic = item.parentElement.parentElement;

			item.style.display = flag ? "" : "none";
			if (flag) {
				//ensure items are visible - not collapsed
				item.parentElement.style.display = "block"
			};
			if (item.parentElement.querySelectorAll('li:not([style*="display: none;"])').length == 0) {
				topic.style.display = "none";
			} else {
				topic.style.display = "block";
			}
		};

		for (const item of items) {
			if (hasFilter) {
				itemText = item.innerText.trim().toLowerCase();
				if (itemText.includes(filter)) {
					showItem(true, item);
				} else {
					showItem(false, item);
				}
			} else {
				showItem(true, item);
			}
		}
	}

	#createWorkItemPanel(viewManager, items) {
		for (const itemKey in items) {
			this.addHeaderWorkIcon(items[itemKey]);
		}
	}

	initializeWith(viewManager, content) {
		this.#createTopicList(viewManager, content.topicList);
		this.#createWorkItemPanel(viewManager, content.workpanelItems);
	}

	isCollapsed() {
		return this.elem.menuIcon.classList.contains("rot90");
	}

	toggleCollapse() {
		if (!this.isCollapsed()) {
			this.elem.topicList.style.display = "none";
			this.elem.body.style.display = "none";
			this.elem.menuIcon.classList.toggle("rot90");
			this.sidebarElem.style.width = "50px";
		} else {
			this.elem.topicList.style.display = "block";
			this.elem.body.style.display = "block";
			this.elem.menuIcon.classList.toggle("rot90");
			this.sidebarElem.style.width = "225px";
		}
	}

	/**
	 * { title: , id: , iconName: , action: }
	 */
	addHeaderWorkIcon(def) {
		let icon = null;
		let workIconBar = this.elem.workIconBar;
		if (def.iconName) {
			icon = this.#newWorkIcon(def.id, def.iconName);
			icon.elem.title = def.title ? def.title : "";
			workIconBar.append(icon.elem);

			let item = { resolved: false };
			item.def = def;
			item.icon = icon;
			item.elem = icon.elem;

			onClicked(item.elem, (evt) => {
				this.#callItemFunction(item.def.action, (actionObj) => {
					if (!item.resolved) {
						//custom property for direct actions 
						item.elem.action = actionObj;
						item.resolved = true;
					}
					item.elem.action(evt);
				});
			});
		}
		return icon;
	}

	clickItem(itemText) {
		let item = Array.from(this.sidebarElem.querySelectorAll('li'))
			.find(li => li.textContent.trim() === itemText.trim());
		if (item) {
			item.click();
		}
	}

}

let sidebarComp = null;

/**
 * Public
 */
export function getComponent(anchorElement) {
	if (!sidebarComp) {
		sidebarComp = new Sidebar(anchorElement);
	}
	return sidebarComp;
}
