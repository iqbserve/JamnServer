/* Authored by iqbserve.de */

import { ViewDialog, loadServerStyleSheet } from '../jsmod/view-classes.mjs';
import { onClicked, onKeydown, onFocus, reworkHtmlElementIds, KEY } from '../jsmod/uibuilder.mjs';
import { WorkbenchInterface as WbApp } from '../jsmod/workbench.mjs';
import * as Icons from '../jsmod/icons.mjs';

/**
 * Demo LogIn module based on a internal html source shown in a modal dialog.
 */
class LoginDialog extends ViewDialog {

	showPasswordIcon;

	constructor(html) {
		super("");
		this.viewSource.setHtml(html);
	}

	reworkHtml(html) {
		//make the ids of the html source local
		html = reworkHtmlElementIds(html, this.uid.get());
		return html;
	}

	beforeCreateViewElement() {
		loadServerStyleSheet("/jsmod/html-components/login.css");
	}

	initialize() {
		super.initialize();

		this.elementsToProperties(["pbLogin", "username", "password", "message", "successMessage", "pbLoginSuccess"]);

		this.showPasswordIcon = Icons.eye(this.getElement("showPasswordIcon"));
		onClicked(this.showPasswordIcon.elem, () => { this.togglePasswordVisibility(); });

		Icons.user(this.getElement("lbUsername"));
		Icons.password(this.getElement("lbPassword"));
		Icons.loginAction(this.getElement("loginActionIcon"));

		onKeydown(this.password, (evt) => {
			if (KEY.isEnter(evt) && this.username.value.length > 0 && this.password.value.length > 0) {
				evt.preventDefault();
				dialogLoginAction(this);
			}
		});

		this.setTitle("Jamn System LogIn")
		this.setAction("pbLogin", () => dialogLoginAction(this))
			.setAction("pbLoginSuccess", () => this.close());

		this.dialog().style["margin-top"] = ViewDialog.default.styleProps["margin-top"];
	}

	getElement(id) {
		//overwritten - to switch to local context ids
		return super.getElement(this.uid.get(id));
	}

	togglePasswordVisibility() {
		this.showPasswordIcon.toggle();
		this.password.type = this.password.type === "password" ? "text" : "password";
		this.password.focus();
	}

	showRunning(flag) {
		super.showRunning(flag);
		this.setDisabled(flag, flag ? "wait" : "default");
	}

	showMessage(msgText, showFlag) {
		this.showRunning(false);
		this.message.innerHTML = showFlag ? msgText : "";
		this.setDisplay(this.message, showFlag);
	}

	showSuccessMessage() {
		this.showRunning(false);
		this.setDisplay(this.successMessage, "inline-block");
		this.pbLoginSuccess.focus();
	}

	reset() {
		this.username.value = "";
		this.password.value = "";
		if(!(this.password.type === "password")){
			this.togglePasswordVisibility();
		}
		this.showMessage("", false);
		this.setDisplay(this.successMessage, false);
	}

	beforeOpen() {
		this.reset();
	}
}

/**
 */
let tries = 0;
let accessToken = null;

let sidebarLoginIcon = Icons.newIcon(Icons.login(), document.getElementById("sidebar-icon-login"));
let sidebarLoginItem = document.getElementById("sidebar-item-login");

//create the dialog instance
let dialog = new LoginDialog(loginViewHtml());

/**
 */
export function isLoggedIn() {
	return accessToken != null;
}

/**
 */
export function processSystemLogin() {

	if (isLoggedIn()) {
		WbApp.confirm({
			message: "<b>Log Off</b><br>Do you want to Log Off from the Server System?"
		}, (value) => value ? doLogOff() : null);
	} else {
		tries = 0;
		dialog.open();
	}
}

/**
 * Internals
 */
/**
 */
function dialogLoginAction(dialog) {

	dialog.showRunning(true);
	//simulate a time consuming login
	simulateLoginTime(tries == 0 ? 1000 : 500).then(() => {
		tries++;

		if (tries < 2) {
			let text = `We are sorry, unfortunately the first login attempt failed for demo reason.<br><b>Please click again ...`;
			dialog.showMessage(text, true);
		}
		if (tries > 1) {
			dialog.showSuccessMessage();
			toggleStatus();
		}
	});
}

function simulateLoginTime(ms) {
	return new Promise(resolve => {
		setTimeout(() => resolve(), ms);
	});
}

/**
 */
function doLogOff() {
	toggleStatus();
}

/**
 */
function toggleStatus() {

	sidebarLoginIcon.toggle((icon) => {
		if (!isLoggedIn()) {
			icon.style.color = "green";
			icon.title = "Log Off";
			sidebarLoginItem.innerHTML = "Log Off";

			accessToken = "jwt bearer";
		} else {
			icon.style.color = "";
			icon.title = "Login";
			sidebarLoginItem.innerHTML = "Login";

			accessToken = null;
		}
	});
}

function loginViewHtml() {
	return `
<div class="login-view">
    <div class="login-view-row">
        <label id="lbUsername" class="login-label" for="username"></label>
        <input id="username" class="login-input" placeholder="Username" type="text">
    </div>
    <div class="login-view-row">
        <label id="lbPassword" class="login-label" for="password"></label>
		<input id="password" class="login-input" placeholder="Password" type="password" >
		<i id="showPasswordIcon" class="login-show-password-icon" title="Show/Hide password"></i>
    </div>
    <hr class="solid">
    <div class="login-view-button-row">
        <button id="pbLogin" class="login-button" style="width: 100%">
            <i id="loginActionIcon"></i>
            <span style="margin-left: 10px;">Login</span>
        </button>
    </div>
    <div id="message" class="login-view-message-row"></div>
    <div id="successMessage" class="view-dialog-view-area-overlay">
        <h2>Welcome</h2>
        <img src="images/handshake.png" alt="Login success" style="width: 120px; height: 120px;">
        <p style="margin-top: 0px;">you got successfully logged in</p>
        <button id="pbLoginSuccess" class='login-button'
            style="display: inline; font-size: 18px; width: 250px; margin-top: 5px;">
            have a nice day ...
        </button>
    </div>
</div>
`}

