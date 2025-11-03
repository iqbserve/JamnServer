/* Authored by iqbserve.de */

import { CommandDef } from '../jsmod/data-classes.mjs';
import { LazyFunction } from '../jsmod/tools.mjs';
import * as Icons from '../jsmod/icons.mjs';


/**
 * <pre>
 * Data object that defines the content of the Workbench Sidebar.
 * The structure is:
 * n Topics 
 *  - 1 Topic 
 *    - n Items
 * Normally lazy functions are used to provide the functionality for an item click.
 * That prevents the corresponding modules from being loaded at sidebar definition time.
 * </pre>
 */
export const topicList = {
	system: {
		iconName: Icons.gi_system(), title: "System",
		items: [
			//create a view item with a singleton view
			{ title: "Infos", view: new LazyFunction('../jsmod/system-infos.mjs', "getView") },

			//create a functional item with id and an action
			{ title: "Login", id: "sidebar-item-login", action: new LazyFunction('../jsmod/login.mjs', "processSystemLogin").asAction() }
		]
	},

	commands: {
		iconName: Icons.command(), title: "Commands",
		items: [
			//create new views from the same type with identifying names and data objects 
			{
				title: "Sample: shell test",
				view: new LazyFunction('../jsmod/command.mjs', "getView", "shellSampleView") ,
				data: new CommandDef("Sample: [sh command]", "runjs", "/sample/sh-test.mjs", { args: true })
			},
			{
				title: "Sample: build test",
				view: new LazyFunction('../jsmod/command.mjs', "getView", "buildSampleView") ,
				data: new CommandDef("Sample: [build script]", "runjs", "/sample/build-project-test.mjs")
			},
			{
				title: "Sample: extension",
				view: new LazyFunction('../jsmod/command.mjs', "getView", "extensionSampleView") ,
				data: new CommandDef("Sample: [extension command]", "runext", "sample.Command", { args: true })
			}
		]
	},

	tools: {
		iconName: Icons.tools(), title: "Tools",
		items: [
			{ title: "DB Connections", view: new LazyFunction('../jsmod/db-connections.mjs', "getView") }
		]
	}
}

export const workpanelItems = {

	loginIcon : { title: "Login", id: "sidebar-icon-login", iconName: Icons.login(), action: new LazyFunction('../jsmod/login.mjs', "processSystemLogin").asAction() } 

}