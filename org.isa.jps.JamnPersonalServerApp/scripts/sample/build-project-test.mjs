/* Authored by iqbserve.de */

import {echo, sh, workspacePath, isOnUnix} from "tools.mjs";

/**
 * A playful "JS build script" example to build the JamnServer project 
 * with git and maven in the workspace folder.
 *  
 * REQUIRES:
 *  - a callable git and a maven >= v3.6 installation
 * Note: since the default server port (8099) is used by the unit tests, 
 * the executing JamnPersonalServerApp must use a different port.
 * 
 * Run on the JPS CLI console jps> runjs sample/build-project.mjs 
 * 
 * see also: \http\jsmod\sidebar-content.mjs
 */
let projectName = "JamnServer"
let projectGitUrl = `https://github.com/integrating-architecture/${projectName}.git`;
let workspace = workspacePath();
let wsLocalMvnRepo = workspacePath(".m2ws");


//print build output to console
let outputConsumer = (line) => {
	console.log(line);
};

function buildProject(){
	
	echo(`Start build project [${projectName}] from [${projectGitUrl}]`);
	
	//clear workspace dir
	let cmd = isOnUnix() ? `rm -rf ${projectName}` : `rd /s /q ${projectName}`;
	sh(cmd, workspace, outputConsumer);
	
	//clone git project
	sh(`git clone --verbose ${projectGitUrl}`, workspace, outputConsumer);
	
	//call maven install
	//using a workspace local .m2 repo and -B for batch mode supressing colored output
	sh(`mvn -B "-Dmaven.repo.local=${wsLocalMvnRepo}" install`
		, workspacePath(projectName)
		, outputConsumer);

}

buildProject();
