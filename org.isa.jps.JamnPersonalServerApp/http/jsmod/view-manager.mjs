/* Authored by iqbserve.de */

import { StandardDialog } from './view-classes.mjs';

/**
 * <pre>
 * A simple manager to handle the on demand view html loading
 * and the workarea display logic.
 * 
 * A view element is instantiated as the child of a "view cartridge div""
 * that becomes added to the workbench-workarea.
 * </pre>
 * 
 */
export class WorkbenchViewManager {

	workarea;
	registeredViews;

	//current implementation supports only ONE modal dialog at a time
	//it is implicit a singleton
	standardDlg;

	getAsCartridgeId = (viewId) => "view.cartridge." + viewId;

	constructor(workarea) {
		this.workarea = workarea;
		this.registeredViews = {};

		this.standardDlg = new StandardDialog();
	}

	registerView(view, viewData) {
		this.registeredViews[view.id] = { view: view, data: viewData, cart: null };
	}

	setViewCartVisible(viewCart, flag) {
		if (flag) {
			viewCart.style["display"] = "block";
			viewCart.style["visibility"] = "visible";
		} else if (this.workarea.children.length > 0) {
			viewCart.style["display"] = "none";
		}
	}

	createViewCartridge(viewId, viewElement) {
		let viewCart = document.createElement("div");
		viewCart.id = this.getAsCartridgeId(viewId);
		viewCart.style = "visibility: visible; display: block;"
		viewCart.appendChild(viewElement);

		this.registeredViews[viewId].cart = viewCart;
		return viewCart;
	}

	closeAllCloseableViews() {
		for (let key in this.registeredViews) {
			let viewItem = this.registeredViews[key];
			if (viewItem.cart) {
				this.closeView(viewItem);
			}
		}
	}

	closeView(viewItem) {
		// view is expected to handle close itself 
		// and return true if it was closeabel and did close
		if (viewItem.view.close()) {
			this.setViewCartVisible(viewItem.cart, false);
		}
	}

	scrollToTop() {
		this.workarea.scrollTop = 0;
	}

	openView(viewItem) {
		this.closeAllCloseableViews();

		viewItem.view.open();
		this.moveView(viewItem.view, 1);
		this.setViewCartVisible(viewItem.cart, true);
		this.scrollToTop();
	}

	#getWorkViewOf(viewElem) {
		viewElem = viewElem.parentElement;
		while(viewElem  && !viewElem.classList.contains("work-view")){
			viewElem = viewElem.parentElement;
		}
		return viewElem;
	}

	//default action requests 
	onViewAction(evt, action) {
		let workView = this.#getWorkViewOf(evt.target);
		let viewItem = this.registeredViews[workView.id];

		if (!viewItem) {
			throw new Error(`UNKNOWN WorkView [${workView.id}]`);
		}

		if ("close" === action) {
			this.closeView(viewItem);
		} 
	}

	getVisibleChildren() {
		let children = [];
		for (let child of this.workarea.children) {
			if (child.style.display == "block") {
				children.push(child);
			}
		}
		return children;
	}

	stepViewsDown() {
		let children = this.getVisibleChildren();
		if (children.length > 1) {
			this.workarea.insertBefore(children[children.length - 1], children[0]);
			this.scrollToTop();
		}
	}

	stepViewsUp() {
		let children = this.getVisibleChildren();
		if (children.length > 1) {
			this.workarea.insertBefore(children[0], null);
			this.scrollToTop();
		}
	}

	moveView(view, position) {
		let elemCount = this.workarea.children.length;
		let viewCart = this.registeredViews[view.id].cart;
		let pos = -1;

		if (isNaN(position)) {
			let idx = 0;
			if (position === "up") {
				idx = Array.prototype.indexOf.call(this.workarea.children, viewCart) - 1;
			} else if (position === "down") {
				idx = Array.prototype.indexOf.call(this.workarea.children, viewCart) + 1;
			} else {
				return;
			}
			//always add 1
			//cause position is expected to be a human counter 1...n 
			//NOT array idx 0...n
			position = (idx + 1).toString();
		}

		pos = parseInt(position);
		pos = pos <= 0 ? 1 : pos;

		if (elemCount > 1) {
			if (pos >= elemCount) {
				this.workarea.removeChild(viewCart);
				this.workarea.appendChild(viewCart);
			} else if (pos - 1 >= 0) {
				this.workarea.removeChild(viewCart);
				this.workarea.insertBefore(viewCart, this.workarea.children[pos - 1]);
			}
		}
	}

	// ViewManager public view open request method for components
	// in this case the sidebar
	onComponentOpenViewRequest(viewItemId, comp=null) {
		let viewItem = this.registeredViews[viewItemId]

		if (viewItem) {
			if (viewItem.cart) {
				this.openView(viewItem);
			} else {
				viewItem.view.getViewElement((element) => {
					let viewCart = this.createViewCartridge(viewItem.view.id, element);
					this.workarea.prepend(viewCart);
					this.openView(viewItem);
				});
			}
		}
	}

	promptUserInput(text, value, cb) {
		this.standardDlg.openInput(text, value, cb);
	}

	promptConfirmation(text, cb){
		this.standardDlg.openConfirmation(text, cb);
	}
}


