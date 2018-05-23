// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.templates;

import java.util.Collection;

import com.yahoo.text.XML;

/**
 * A set of variable bindings for template rendering
 *
 * @author bratseth
 */
public abstract class Context {

    private boolean xmlEscape = true;

    // These may be wrapped in an object if it gets unruly like this...
    private String boldOpenTag;
    private String boldCloseTag;
    private String separatorTag;

    private boolean utf8Output = false;

    //prevent sub-classing outside of this package.
    Context() {}

    // set|getXmlEscape no longer final on cause of HitContext subclassing _and_ wrapping Context
    /** Sets whether this context should xml-escape returned values */
    public void setXmlEscape(boolean xmlEscape) { this.xmlEscape=xmlEscape; }

    /** Returns whether this context xml-escapes returned values. Default is true */
    public boolean getXmlEscape() { return xmlEscape; }

    /**
     * Makes a <b>secondary</b> binding
     *
     * @return the old value bound to this key, or null it the key was previously unbound
     */
    public abstract Object put(String key,Object value);

    /**
     * <p>Returns a value by looking it up in the primary,
     * and thereafter in secondary sources.</p>
     *
     * <p>If xml escaping is on and this is a string, xml attribute escaping is done
     * </p>
     */
    abstract public Object get(String key);

    /**
     * Removes a <b>secondary</b> binding
     *
     * @return the removed value, or null if it wasn't bound
     */
    public abstract Object remove(Object key);


    // These three may be collapsed to one method
    public void setBoldOpenTag(String boldOpenTag) {
        this.boldOpenTag = boldOpenTag;
    }
    public void setBoldCloseTag(String boldCloseTag) {
        this.boldCloseTag = boldCloseTag;
    }
    public void setSeparatorTag(String separatorTag) {
        this.separatorTag = separatorTag;
    }


    protected Object normalizeValue(Object value) {
        if (value == null) {
            return "";
        } else if (xmlEscape && value instanceof String) {
            return XML.xmlEscape((String) value, true, null);
        } else {
            return value;
        }
    }

    public String getBoldOpenTag() {
        return boldOpenTag;
    }

    public String getBoldCloseTag() {
        return boldCloseTag;
    }

    public String getSeparatorTag() {
        return separatorTag;
    }

    public abstract Collection<? extends Object> getKeys();

    /**
     * Used by the template to decide whether to use UTF-8 optimizations.
     *
     * @return whether the result encoding is UTF-8
     */
    public boolean isUtf8Output() {
        return utf8Output;
    }

    /**
     * Used by the template to decide whether to use UTF-8 optimizations.
     * TODO: TVT: Make this package private again
     * @param utf8Output whether the output encoding is UTF-8
     */
    public void setUtf8Output(boolean utf8Output) {
        this.utf8Output = utf8Output;
    }

}
