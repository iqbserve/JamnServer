/*jamn.web.template*/

/**
 * A sample js app module
 */
export function anchorAt (rootId) {
	rootElement = document.getElementById(rootId);
	document.addEventListener("DOMContentLoaded", startApp);
}

//export const ServerInfo = {"name":"JamnServer - Sample WebContent App","version":"0.0.1-SNAPSHOT","description":"A simple Web Content Provider sample app.","links":{"app.scm":"https://github.com/integrating-architecture/JamnServer/blob/master/org.isa.ipc.JamnWebContentProvider"}};
//this will get injected on the server side - see FileEnricherValueProvider
export const ServerInfo = ${server.info};

/**
 * Private section
 */
let rootElement = null;

//this is called after document load but before getting visible
function startApp () {
	prepare();
	show();
}

function prepare () {
	setHTML("server.name", ServerInfo.name);
	setHTML("server.version", ServerInfo.version);
	setHTML("server.description", (ServerInfo.description.length > 15) ? "<br>"+ServerInfo.description : ServerInfo.description);
	
	setAttr("app.scm", "href", ServerInfo.links["app.scm"]);
}

function show () {
	rootElement.style.visibility = 'visible';
}

function setHTML (id, html){
	document.getElementById(id).innerHTML = html;
}

function setAttr (id, name, value){
	document.getElementById(id).setAttribute(name, value);
}
