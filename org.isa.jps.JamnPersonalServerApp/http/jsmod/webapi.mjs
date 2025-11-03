import { WbProperties } from '../jsmod/workbench-properties.mjs';

/**
 * The module provides web service url name constants
 * like: [root]/[endpoint] e.g. "webapi/system/get-infos"
 */

const urlRoot = WbProperties.getOrDefault("webServiceUrlRoot", "/webapi");

function get(endpoint) {
    return urlRoot + endpoint;
}

/**
 * Url name constants
 */
export const system_getinfos = get("/system/get-infos");
export const system_updateinfos = get("/system/update-infos");

export const service_shellcmd = get("/service/shell-cmd");

export const service_get_dbconnections = get("/service/get-db-connections");
export const service_save_dbconnections = get("/service/save-db-connections");
export const service_delete_dbconnections = get("/service/delete-db-connections");
