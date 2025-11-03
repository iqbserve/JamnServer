
/**
 * A simple properties class
 */
class Properties {
    showIntro = true;
    autoStartView =  "";
    webServiceUrlRoot = null; // "/webapi"
    webSocketUrlRoot = null; // "/wsoapi"
    webBackendServerUrl = null; // "https://iqbserve.de:9090"

    getOrDefault(key, defaultVal){
        return this[key] || defaultVal; 
    }
}

export const WbProperties = new Properties();
