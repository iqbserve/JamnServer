/* Authored by iqbserve.de */

import { typeUtil, mergeArrayInto } from '../jsmod/tools.mjs';
import * as Icons from '../jsmod/icons.mjs';

/**
 */
export function newUIId(prefix = null) {
    let id = Math.random().toString(32).slice(5);
    return prefix ? prefix + "-" + id : id;
}

export function reworkHtmlElementIds(html, contextIdVal, ignoreList = []) {
    html = html.replace(/id\s*=\s*"([^"]*)"/g, (expr, val) => {
        if (!ignoreList.includes(val)) {
            return `id="${val + "-" + contextIdVal}"`
        }
        return `id="${val}"`
    });
    html = html.replace(/for\s*=\s*"([^"]*)"/g, (expr, val) => {
        if (!ignoreList.includes(val)) {
            return `for="${val + "-" + contextIdVal}"`
        }
        return `for="${val}"`
    });
    return html;
}

/**
 */
export class ContextId {
    #uid = newUIId();

    get(prefix = null) {
        return prefix ? prefix + "-" + this.#uid : this.#uid;
    }
}

/**
 */
function isDataAttribute(name) {
    return name.startsWith("data-");
}

/**
 * <pre>
 * An experimental factory/builder to programmatically create UI components and views
 * using a combination of method chaining and chained closures.
 * 
 * A UIBuilder instance is the starting point.
 * It just serves as a dataobject and as a static function provider.
 * 
 * The actual builder objects are instances of UIComp
 * - uc = builder.newUIComp(...)
 * 
 * A UIComp is a lightweight wrapper around a domElem
 * providing the chainable methods and the closure entry.
 * 
 * - uc.addLabelButton({ text: "Command:", clazzes: ["cls1", "cls2" ...] })
 *      .attrib({"title": "Run", ...})
 *      .style({ "align-items": "flex-start", "text-align": "center", ... })
 *      ...
 *      .addFieldset( {title: "Details"}, (fieldset) => {
 *         //closure to work with the new fieldset comp
 *         fieldset.add("h3") ...
 *         ... 
 *       })
 *    ...
 * ...
 * 
 * </pre>
 */
export class UIBuilder {

    static #setterAttributes = ["name"];
    static #valueClearableInputTypes = ["text", "password"];

    allowedDefAttributes = ["name", "title"];

    //instance variables
    #defaultCompProps = new DefaultViewCompProps();

    //all elements with a varid are put to the collection
    //elem = elementCollection.<varid>
    elementCollection = {};

    //collection for any objects
    objectCollection = {};
    collectableAttributes = ["data-bind"];

    #UICompFactory = {
        newComp: (builder, parentComp, domElem) => {
            return new UIComp(builder, parentComp, domElem);
        }
    };

    static clearControl(domElem) {

        let tagName = domElem.tagName.toLowerCase();
        if (tagName === "input") {
            if (UIBuilder.#valueClearableInputTypes.includes(domElem.type)) {
                domElem.value = "";
            }
        } else if (tagName === "textarea") {
            domElem.value = "";
        }
    }

    static createDomElementFrom(html, tagName = "template") {
        let template = document.createElement(tagName);
        if (html) {
            template.innerHTML = html;
        }
        if (tagName.toLowerCase() == "template") {
            return template.content.firstElementChild;
        }
        return template;
    }

    static removeChildFrom(parent, id) {
        let node = UIBuilder.getChildFrom(parent, id);
        if (node) { parent.removeChild(node); }
    }

    static getChildFrom(parent, id) {
        for (let child of parent.childNodes) {
            if (child.id === id) { return child; }
        }
        return null;
    }

    static reworkId(id) {
        if (!id || id === 'undefined' || id === "") {
            return Math.random().toString(32).slice(5);
        }
        return id;
    }

    static setClassesOf(domElem, clazzes, defaultClazzes = null) {
        if (typeUtil.isArray(clazzes)) {
            clazzes.forEach(clazz => domElem.classList.add(clazz));
        } else if (clazzes) {
            domElem.classList.add(clazzes.trim());
        } else if (defaultClazzes) {
            UIBuilder.setClassesOf(domElem, defaultClazzes, null);
        }
    }

    static setStyleOf(domElem, styleProps) {
        for (const name in styleProps) {
            domElem.style[name] = styleProps[name];
        }
    }

    static setAttributesOf(domElem, attributeProps) {
        for (const name in attributeProps) {
            if (UIBuilder.#setterAttributes.includes(name) || isDataAttribute(name)) {
                domElem.setAttribute(name, attributeProps[name]);
            } else {
                domElem[name] = attributeProps[name];
            }
        }
    }

    static linkLabelToElement(label, elem) {
        label.htmlFor = elem.id;
    }

    static loadServerStyleSheet(path) {
        if (!UIBuilder.queries.hasStyleSheet(path)) {
            let cssLink = document.createElement('link');
            cssLink.rel = 'stylesheet';
            cssLink.type = 'text/css';
            cssLink.href = path;
            document.head.appendChild(cssLink);
            return true;
        }
        return false;
    }

    static queries = {
        findLabelByName: (rootElem, nameVal) => {
            return rootElem.querySelector(`label[name='${nameVal}']`);
        },
        findElementByName: (rootElem, elemType, nameVal) => {
            return rootElem.querySelector(`${elemType}[name='${nameVal}']`);
        },
        findElementByName2: (rootElem, elemType, nameVal) => {
            return Array.from(rootElem.querySelectorAll(elemType))
                .find(elem => elem.name === nameVal);
        },
        hasStyleSheet: (path) => {
            return !!document.head.querySelector(`link[rel="stylesheet"][href="${path}"]`);
        }
    }

    setElementCollection(collection) {
        this.elementCollection = collection;
        return this;
    }

    setObjectCollection(collection) {
        this.objectCollection = collection;
        return this;
    }

    collectingDisable() {
        return !this.elementCollection || !this.objectCollection;
    }

    registerElement(key, elem) {
        if (this.collectingDisable()) { return; }
        this.elementCollection[key] = elem;
    }

    registerObject(key, obj, context = null) {
        if (this.collectingDisable()) { return; }

        if (context && !this.objectCollection[context]) {
            this.objectCollection[context] = {};
        }
        if (context) {
            this.objectCollection[context][key] = obj;
        } else {
            this.objectCollection[key] = obj;
        }
    }

    forEachElement(cb) {
        if (this.collectingDisable()) { return; }

        let elements = this.elementCollection;
        let names = Object.getOwnPropertyNames(elements);
        names.forEach((name) => {
            let domElem = elements[name];
            cb(name, domElem);
        });
    }

    forEachBinding(cb) {
        if (this.collectingDisable()) { return; }

        let bindings = this.objectCollection.bindings;
        let names = Object.getOwnPropertyNames(bindings);
        names.forEach((name) => {
            let domElem = bindings[name];
            cb(name, domElem);
        });
    }

    getDataListFor(name) {
        if (this.collectingDisable()) { return; }
        return this.objectCollection[this.elementCollection[name].list.id];
    }

    setDefaultCompProps(compPropsObj) {
        this.#defaultCompProps = compPropsObj;
        return this;
    }

    setCompPropDefaults(cb) {
        cb(this.#defaultCompProps);
        return this;
    }

    getDefaultCompProps() {
        return this.#defaultCompProps;
    }

    getUICompFactory() {
        return this.#UICompFactory;
    }

    setUICompFactory(factoryMethod) {
        this.#UICompFactory.newComp = factoryMethod;
        return this;
    }

    newUICompFor(domElem) {
        return this.#UICompFactory.newComp(this, null, domElem);
    }

    newUIComp(typeId = "comp") {
        let defaults = this.getDefaultCompProps().get(typeId);
        let comp = this.#UICompFactory.newComp(this, null, null)
            .initialize({ elemType: defaults.elemType, "typeId": typeId });
        return comp;
    }
}

/**
 */
export class UIComp {
    domElem;
    parentComp;
    builder;

    constructor(builder, parent, domElem = null) {
        this.builder = builder;
        this.parentComp = parent;
        this.domElem = domElem;
    }

    /**
     * ensur that the argument signature
     * (def=dataobject, cb=callback function)
     * is retained
     */
    resolveArgs(argDef, argCb, resultCb) {
        if (typeUtil.isFunction(argDef)) {
            argCb = argDef;
            argDef = {};
        } else if (typeUtil.isString(argDef)) {
            argDef = { elemType: argDef };
        } else if (!argDef) {
            argDef = {};
        }
        argCb = argCb || (() => { });
        resultCb(argDef, argCb);
    }

    createDomElement(def) {
        this.domElem = document.createElement(def.elemType);
    }

    applyDefProperties(def) {
        if (def.clazzes) {
            this.class(def.clazzes);
        }

        let allowed = this.getBuilder().allowedDefAttributes;
        let attributes = {};
        for (const key of Object.keys(def)) {
            if (allowed.includes(key) || isDataAttribute(key)) {
                attributes[key] = def[key];
            }
        }

        UIBuilder.setAttributesOf(this.domElem, attributes);
    }

    applyDefaulClasses(def) {
        if (!def.nodefaults) {
            let defaultClasses = this.getDefaultCompProps().getClassesFor(def.typeId);
            if (defaultClasses) {
                this.class(defaultClasses);
            }
        }
    }

    applyDefaultStyle(def) {
        if (!def.nodefaults) {
            let defaultStyle = this.getDefaultCompProps().getStylesFor(def.typeId);
            if (defaultStyle) {
                this.style(defaultStyle);
            }
        }
    }

    addElementToTarget(targetElem, elem, def) {
        if (def.pos == "top" || def.pos == 0) {
            targetElem.prepend(elem);
        } else if (def.pos > 0) {
            targetElem.insertBefore(elem, targetElem.childNodes[def.pos]);
        } else {
            targetElem.append(elem);
        }
    }

    registerElement(def, elem) {
        if (def.varid) {
            this.getBuilder().registerElement(def.varid, elem);
        }
    }

    registerObject(key, obj, context = null) {
        this.getBuilder().registerObject(key, obj, context);
    }

    collectAttributesFrom(domElem) {
        let names = this.getBuilder().collectableAttributes;
        let value = null;
        for (const name of names) {
            value = domElem.getAttribute(name);
            if (value && name === "data-bind") {
                this.registerObject(value, domElem, "bindings");
            }
        }
    }

    setupIconFor(domElem, def) {
        if (def.iconName) {
            return Icons.newIcon(def.iconName).apply(domElem);
        }
    }

    linkLabelToElement(label, elem) {
        label = label instanceof UIComp ? label.domElem : label;
        elem = elem instanceof UIComp ? elem.domElem : elem;
        UIBuilder.linkLabelToElement(label, elem);
    }

    isReadOnly(def) {
        return def.hasOwnProperty('readOnly');
    }

    newDataList(domElem, data) {
        let datalist = new DataList(domElem);
        datalist.setOptions(data);
        this.registerObject(datalist.listElem.id, datalist);
        return datalist;
    }

    /**
     * central method
     */
    addNewCompImpl(def) {
        let comp = this.getBuilder().getUICompFactory().newComp(null, this, null)
            .initialize(def);

        this.addCompObjImpl(def, comp);
        return comp;
    }

    addCompObjImpl(def, compObj) {
        compObj.parent = this;
        this.registerElement(def, compObj.domElem);
        this.addElementToTarget(this.domElem, compObj.domElem, def);
    }

    finishAdd(def, comp) {
        return this;
    }

    addContainerImpl(typeId, def, cb) {
        def.elemType = def.elemType || this.getDefaultCompProps().get(typeId)?.elemType || "span";
        def.typeId = typeId;

        let comp = this.addNewCompImpl(def);
        cb(comp);
        return this.finishAdd(def, comp);
    }

    initialize(def) {
        this.createDomElement(def);
        this.applyDefaulClasses(def);
        this.applyDefaultStyle(def);
        this.applyDefProperties(def);
        return this;
    }

    class(clazzes) {
        UIBuilder.setClassesOf(this.domElem, clazzes);
        return this;
    }

    attrib(attribProps) {
        UIBuilder.setAttributesOf(this.domElem, attribProps);
        this.collectAttributesFrom(this.domElem);
        return this;
    }

    style(styleProps) {
        UIBuilder.setStyleOf(this.domElem, styleProps);
        return this;
    }

    html(val) {
        if (val || val === "") { this.domElem.innerHTML = val };
        return this;
    }

    title(val) {
        if (val || val === "") { this.domElem.title = val };
        return this;
    }

    linkToLabel(labelName) {
        let labelElem = UIBuilder.queries.findLabelByName(this.getRootComp().domElem, labelName);
        this.linkLabelToElement(labelElem, this.domElem);
        return this;
    }

    getBuilder() {
        if (this.parentComp && !this.builder) {
            return this.parentComp.getBuilder();
        }
        return this.builder;
    }

    getDomElem() {
        return this.domElem;
    }

    getRootComp() {
        let comp = this;
        while (comp.parentComp) { comp = comp.parentComp };
        return comp;
    }

    getDefaultCompProps() {
        return this.getBuilder().getDefaultCompProps();
    }

    appendTo(elem) {
        elem.append(this.domElem);
        return this;
    }

    prependTo(elem) {
        elem.prepend(this.domElem);
        return this;
    }

    add(def, cb) {
        this.resolveArgs(def, cb, (resDef, resCb) => { def = resDef; cb = resCb; });

        let comp = this.addNewCompImpl(def);
        cb(comp);
        return this.finishAdd(def, comp);
    }

    addFromHtml(html, cb = null) {
        let template = document.createElement("template");
        template.innerHTML = html;

        let elements = [...template.content.childNodes].filter(n => n.nodeType === Node.ELEMENT_NODE);
        for (const element of elements) {
            this.domElem.append(element);
        }
        if (cb) { cb(elements); }
        return this;
    }

    addContainer(def, cb) {
        this.resolveArgs(def, cb, (resDef, resCb) => { def = resDef; cb = resCb; });
        return this.addContainerImpl("container", def, cb);
    }

    addColContainer(def, cb) {
        this.resolveArgs(def, cb, (resDef, resCb) => { def = resDef; cb = resCb; });
        return this.addContainerImpl("colContainer", def, cb);
    }

    addRowContainer(def, cb) {
        this.resolveArgs(def, cb, (resDef, resCb) => { def = resDef; cb = resCb; });
        return this.addContainerImpl("rowContainer", def, cb);
    }

    addDiv(def, cb) {
        this.resolveArgs(def, cb, (resDef, resCb) => { def = resDef; cb = resCb; });
        def.elemType = "div";
        def.typeId = "div";

        let comp = this.addNewCompImpl(def);
        cb(comp);
        return this.finishAdd(def, comp);
    }

    addSpan(def, cb) {
        this.resolveArgs(def, cb, (resDef, resCb) => { def = resDef; cb = resCb; });
        def.elemType = "span";
        def.typeId = "span";

        let comp = this.addNewCompImpl(def);
        cb(comp);
        return this.finishAdd(def, comp);
    }

    addSeparator(def, cb) {
        this.resolveArgs(def, cb, (resDef, resCb) => { def = resDef; cb = resCb; });
        def.elemType = "hr";
        def.typeId = "hr";

        let comp = this.addNewCompImpl(def);
        cb(comp);
        return this.finishAdd(def, comp);
    }

    addList(def, cb) {
        this.resolveArgs(def, cb, (resDef, resCb) => { def = resDef; cb = resCb; });
        def.elemType = def.elemType || "ul";
        def.typeId = "list";

        let comp = this.addNewCompImpl(def);
        cb(comp);
        return this.finishAdd(def, comp);
    }

    addLink(def, cb) {
        this.resolveArgs(def, cb, (resDef, resCb) => { def = resDef; cb = resCb; });
        def.elemType = "a";
        def.typeId = "link";

        let comp = this.addNewCompImpl(def);
        if (def.text) { comp.html(def.text); }

        cb(comp);
        return this.finishAdd(def, comp);
    }

    addActionIcon(def, cb) {
        this.resolveArgs(def, cb, (resDef, resCb) => { def = resDef; cb = resCb; });
        def.elemType = "i";
        def.typeId = "actionIcon";

        let comp = this.addNewCompImpl(def);
        let iconElem = this.setupIconFor(comp.domElem, def);

        cb(comp, iconElem);
        return this.finishAdd(def, comp);
    }

    addFieldset(def, cb) {
        this.resolveArgs(def, cb, (resDef, resCb) => { def = resDef; cb = resCb; });
        def.elemType = "fieldset";
        def.typeId = def.title ? "titledFieldset" : "fieldset";

        let comp = this.addNewCompImpl(def);
        if (def.title) {
            let legend = document.createElement("legend");
            legend.innerHTML = def.title;
            comp.domElem.append(legend);
        }

        cb(comp);
        return this.finishAdd(def, comp);
    }

    addLabel(def, cb) {
        this.resolveArgs(def, cb, (resDef, resCb) => { def = resDef; cb = resCb; });
        def.elemType = def.elemType || "label";
        def.typeId = def.typeId || "label";

        let comp = this.addNewCompImpl(def);
        if (def.text) { comp.html(def.text); }
        if (def.active === false) {
            comp.style({ "pointer-events": "none" });
        }

        cb(comp);
        return this.finishAdd(def, comp);
    }

    addTextField(def, cb) {
        this.resolveArgs(def, cb, (resDef, resCb) => { def = resDef; cb = resCb; });
        def.elemType = "input";
        def.typeId = "textField";

        let comp = this.addNewCompImpl(def);
        comp.domElem.type = "text";
        comp.domElem.id = UIBuilder.reworkId(def.id);

        if (this.isReadOnly(def)) {
            comp.domElem.classList.add(this.getDefaultCompProps().getClassesFor("inputReadOnly"));
            comp.domElem.disabled = true;
        }

        if (def.datalist) {
            let datalist = this.newDataList(comp.domElem, def.datalist);
            this.domElem.prepend(datalist.listElem);
        }

        cb(comp);
        return this.finishAdd(def, comp);
    }

    addButton(def, cb) {
        this.resolveArgs(def, cb, (resDef, resCb) => { def = resDef; cb = resCb; });
        def.elemType = "button";
        def.typeId = "button";

        let comp = this.addNewCompImpl(def);
        comp.domElem.type = "button";
        comp.domElem.id = UIBuilder.reworkId(def.id);

        comp.title(def.title);
        comp.html(def.text);

        if (def.iconName) {
            let iconClasses = [...Icons.getIconClasses(def.iconName), "wkv-button-icon"];
            comp.class(iconClasses);
        }

        cb(comp);
        return this.finishAdd(def, comp);
    }

    addTextArea(def, cb) {
        this.resolveArgs(def, cb, (resDef, resCb) => { def = resDef; cb = resCb; });
        def.elemType = "textarea";
        def.typeId = "textArea";

        let comp = this.addNewCompImpl(def);
        comp.domElem.rows = def.rows;
        comp.domElem.id = UIBuilder.reworkId(def.id);

        if (this.isReadOnly(def)) {
            comp.domElem.classList.add(this.getDefaultCompProps().getClassesFor("textareaReadOnly"));
            comp.domElem.disabled = true;
        }

        cb(comp);
        return this.finishAdd(def, comp);
    }

    addLabelTextField(labelDef, fieldDef, cb) {
        this.resolveArgs(labelDef, cb, (resDef, resCb) => { labelDef = resDef; cb = resCb; });
        this.resolveArgs(fieldDef, cb, (resDef, resCb) => { fieldDef = resDef; cb = resCb; });

        let newComp = {};
        this.addLabel(labelDef, (comp) => { newComp.label = comp; });
        this.addTextField(fieldDef, (comp) => { newComp.textField = comp; });

        this.linkLabelToElement(newComp.label, newComp.textField);

        cb(newComp.label, newComp.textField);
        return this;
    }

    addLabelTextArea(labelDef, areaDef, cb) {
        this.resolveArgs(labelDef, cb, (resDef, resCb) => { labelDef = resDef; cb = resCb; });
        this.resolveArgs(areaDef, cb, (resDef, resCb) => { areaDef = resDef; cb = resCb; });

        let newComp = {};
        this.addLabel(labelDef, (comp) => { newComp.label = comp });
        this.addTextArea(areaDef, (comp) => { newComp.textArea = comp });

        this.linkLabelToElement(newComp.label, newComp.textArea);

        cb(newComp.label, newComp.textArea);
        return this;
    }

    addLabelButton(labelDef, buttonDef, cb) {
        this.resolveArgs(labelDef, cb, (resDef, resCb) => { labelDef = resDef; cb = resCb; });
        this.resolveArgs(buttonDef, cb, (resDef, resCb) => { buttonDef = resDef; cb = resCb; });

        let newComp = {};
        //by default deactivate label for buttons
        if (!labelDef.hasOwnProperty("active")) {
            labelDef.active = false;
        }
        this.addLabel(labelDef, (comp) => { newComp.label = comp });
        this.addButton(buttonDef, (comp) => { newComp.button = comp });

        this.linkLabelToElement(newComp.label, newComp.button);

        cb(newComp.label, newComp.button);
        return this;
    }
}

/**
 */
export class DataList {
    data;
    ctrl;
    listElem;

    constructor(ctrl) {
        this.ctrl = ctrl;
        this.listElem = document.createElement("datalist");
        this.listElem.id = "data." + ctrl.id;
        this.ctrl.setAttribute("list", this.listElem.id);
    }

    #newOption(item) {
        let option = document.createElement("option");
        option.id = item.id ? item.id : item;
        option.value = item.value ? item.value : option.id;
        return option;
    }

    setOptions(optionValues) {
        let option = null;
        optionValues.forEach(item => {
            option = this.#newOption(item);
            this.listElem.append(option);
        });
    }

    removeOption(id) {
        UIBuilder.removeChildFrom(this.listElem, id);
    }

    addOption(item) {
        let id = item.id ? item.id : item;
        let option = UIBuilder.getChildFrom(this.listElem, id);
        if (option === null) {
            option = this.#newOption(item);
            this.listElem.prepend(option);
        }
    }

    addDataItem(key, item) {
        this.addOption(key);
        this.data[key] = item;
    }

    removeDataItem(key) {
        this.removeOption(key);
        delete this.data[key];
    }
}

const resolveElement = (obj) => {
    if (obj instanceof UIComp) { return obj.domElem }
    return obj;
}

/**
 * Shortcuts for setting event actions
 */
export function onClicked(elem, action) {
    elem = resolveElement(elem);
    elem.onclick = action;
}

export function onDblClicked(elem, action) {
    elem = resolveElement(elem);
    elem.ondblclick = action;
}

export function onInput(elem, action) {
    elem = resolveElement(elem);
    elem.oninput = action;
}

export function onKeyup(elem, action) {
    elem = resolveElement(elem);
    elem.onkeyup = action;
}

export function onKeydown(elem, action) {
    elem = resolveElement(elem);
    elem.onkeydown = action;
}

export function onFocus(elem, action) {
    elem = resolveElement(elem);
    elem.onfocus = action;
}

/**
 * UI default definitions
 */
export class DefaultCompProps {

    static makeACopyOf(source) {
        let newProps = { ...source };
        newProps.clazzes = mergeArrayInto(newProps.clazzes, source.clazzes);
        newProps.attribProps = source.attribProps ? { ...source.attribProps } : {};
        newProps.styleProps = source.styleProps ? { ...source.styleProps } : {};
        delete newProps['clazzFilter'];
        return newProps;
    }

    blankComp = { elemType: "div", clazzes: [], attribProps: {}, styleProps: {} };
    comp = { elemType: "div", clazzes: [], attribProps: {}, styleProps: {} };
    colComp = { elemType: "div", clazzes: ["flex-colcomp"], attribProps: {}, styleProps: {} };
    rowComp = { elemType: "div", clazzes: ["flex-rowcomp"], attribProps: {}, styleProps: {} };

    fieldset = { clazzes: [], attribProps: {}, styleProps: {} };
    titledFieldset = { clazzes: [], attribProps: {}, styleProps: {} };
    container = { elemType: "span", clazzes: [], attribProps: {}, styleProps: {} };
    rowContainer = { elemType: "span", clazzes: ["flex-rowcomp"], attribProps: {}, styleProps: {} };
    colContainer = { elemType: "span", clazzes: ["flex-colcomp"], attribProps: {}, styleProps: {} };

    label = { clazzes: [], attribProps: {}, styleProps: {} };
    labelText = { elemType: "label-text", clazzes: [], attribProps: {}, styleProps: {} };
    link = { clazzes: [], attribProps: {}, styleProps: {} };
    list = { elemType: "ul", clazzes: [], attribProps: {}, styleProps: {} };
    actionIcon = { clazzes: [], attribProps: {}, styleProps: {} };
    button = { clazzes: [], attribProps: {}, styleProps: {} };
    textField = { clazzes: [], attribProps: {}, styleProps: {} };
    textArea = { clazzes: [], attribProps: {}, styleProps: {} };
    hr = { clazzes: ["solid"], attribProps: {}, styleProps: {} };

    inputReadOnly = { clazzes: ["input-readonly"], attribProps: {}, styleProps: {} };
    textareaReadOnly = { clazzes: ["textarea-readonly"], attribProps: {}, styleProps: {} };

    get(id) {
        return this[id];
    }

    apply(ids, srcProps, cb) {
        let targetProps;
        for (const id of ids) {
            targetProps = this[id];
            for (const key in srcProps) {
                if (Object.hasOwn(srcProps, key)) {
                    if (key !== "clazzes") {
                        targetProps[key] = { ...srcProps[key], ...targetProps[key] };
                    }
                }
            }
        }
    }

    getClassesFor(id) {
        return this[id]?.clazzes;
    }
    getStylesFor(id) {
        return this[id]?.styleProps;
    }
    getAttributesFor(id) {
        return this[id]?.attribProps;
    }
}

/**
 */
export class DefaultViewCompProps extends DefaultCompProps {

    blankComp = { elemType: "div", clazzes: [], attribProps: {}, styleProps: {} };
    comp = { elemType: "div", clazzes: ["wkv-comp", "row-comp"], attribProps: {}, styleProps: {} };
    colComp = { elemType: "div", clazzes: ["wkv-comp", "col-comp"], attribProps: {}, styleProps: {} };
    rowComp = { elemType: "div", clazzes: ["wkv-comp", "row-comp"], attribProps: {}, styleProps: {} };

    fieldset = { clazzes: ["wkv-compset"], attribProps: {}, styleProps: {} };
    titledFieldset = { clazzes: ["wkv-compset", "wkv-compset-border"], attribProps: {}, styleProps: {} };
    container = { elemType: "span", clazzes: ["wkv-container"], attribProps: {}, styleProps: {} };
    rowContainer = { elemType: "span", clazzes: ["wkv-container", "row-container"], attribProps: {}, styleProps: {} };
    colContainer = { elemType: "span", clazzes: ["wkv-container", "col-container"], attribProps: {}, styleProps: {} };

    label = { clazzes: ["wkv-label-ctrl"], attribProps: {}, styleProps: {} };
    labelText = { elemType: "label-text", clazzes: [], attribProps: {}, styleProps: {} };
    link = { clazzes: ["wkv-link-ctrl"], attribProps: {}, styleProps: {} };
    list = { elemType: "ul", clazzes: ["wkv-list-ctrl"], attribProps: {}, styleProps: {} };
    actionIcon = { clazzes: ["wkv-action-icon"], attribProps: {}, styleProps: {} };
    button = { clazzes: ["wkv-button-ctrl"], attribProps: {}, styleProps: {} };
    textField = { clazzes: ["wkv-value-ctrl"], attribProps: {}, styleProps: {} };
    textArea = { clazzes: ["wkv-textarea-ctrl"], attribProps: {}, styleProps: {} };
    hr = { clazzes: ["solid"], attribProps: {}, styleProps: {} };

    inputReadOnly = { clazzes: ["input-readonly"], attribProps: {}, styleProps: {} };
    textareaReadOnly = { clazzes: ["textarea-readonly"], attribProps: {}, styleProps: {} };
}

/**
 */
export const KEY = Object.freeze({
    enter: 13, isEnter: (evt) => evt.keyCode == KEY.enter,
    escape: 27, isEscape: (evt) => evt.keyCode == KEY.escape
});