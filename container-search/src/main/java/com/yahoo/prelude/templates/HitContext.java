// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.templates;

import com.yahoo.prelude.hitfield.HitField;
import com.yahoo.prelude.hitfield.JSONString;
import com.yahoo.prelude.hitfield.XMLString;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.StructuredData;
import com.yahoo.search.result.FeatureData;
import com.yahoo.text.XML;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * A context providing all the fields of a hit, and falls back to MapContext behavior for all other keys.
 *
 * @author tonytv
 * @deprecated use a Renderer instead
 */
@SuppressWarnings("deprecation")
@Deprecated // TODO: Remove on Vespa 7
public class HitContext extends Context {

    private final Hit hit;
    private final Context fallbackContext;

    public HitContext(Hit hit, Context fallbackContext) {
        this.hit = hit;
        this.fallbackContext = fallbackContext;
    }

    @Override
    public Object put(String key, Object value) {
        return fallbackContext.put(key, value);
    }

    @Override
    public Object get(String key) {
        Object value = normalizedHitProperty(key);
        return value != null ?
                value :
                fallbackContext.get(key);
    }

    @Override
    public Object remove(Object key) {
        return fallbackContext.remove(key);
    }

    @Override
    public Collection<? extends Object> getKeys() {
        Set<Object> keys = new HashSet<>(fallbackContext.getKeys());
        keys.addAll(hit.fieldKeys());
        return keys;
    }

    @Override
    public void setBoldOpenTag(String boldOpenTag) {
        fallbackContext.setBoldOpenTag(boldOpenTag);
    }

    @Override
    public void setBoldCloseTag(String boldCloseTag) {
        fallbackContext.setBoldCloseTag(boldCloseTag);
    }

    @Override
    public void setSeparatorTag(String separatorTag) {
        fallbackContext.setSeparatorTag(separatorTag);
    }

    @Override
    public String getBoldOpenTag() {
        return fallbackContext.getBoldOpenTag();
    }

    @Override
    public String getBoldCloseTag() {
        return fallbackContext.getBoldCloseTag();
    }

    @Override
    public String getSeparatorTag() {
        return fallbackContext.getSeparatorTag();
    }

    @Override
    //TVT: TODO: Make this package private again.
    public boolean isUtf8Output() {
        return fallbackContext.isUtf8Output();
    }

    @Override
    //TODO: TVT: make this package private again
    public void setUtf8Output(boolean utf8Output) {
        fallbackContext.setUtf8Output(utf8Output);
    }

    @Override
    public void setXmlEscape(boolean xmlEscape) {
        fallbackContext.setXmlEscape(xmlEscape);
    }

    @Override
    public boolean getXmlEscape() {
        return fallbackContext.getXmlEscape();
    }

    @Override
    protected Object normalizeValue(Object value) {
        return fallbackContext.normalizeValue(value);
    }

    private Object normalizedHitProperty(String key) {
        Object value = hit.getField(key);
        return value == null ?
                null :
                normalizeHitFieldValue(value);
    }

    private Object normalizeHitFieldValue(Object value) {
        if (value instanceof HitField) {
            HitField hf = (HitField) value;
            if (getXmlEscape()) {
                return hf.quotedContent(getBoldOpenTag(),
                        getBoldCloseTag(),
                        getSeparatorTag(),
                        true);
            } else {
                return hf.getContent(getBoldOpenTag(),
                        getBoldCloseTag(),
                        getSeparatorTag());
            }
        } else if (value instanceof StructuredData) {
            return value.toString();
        } else if (value instanceof XMLString || value instanceof JSONString) {
            return value.toString();
        } else if (getXmlEscape()) {
            return XML.xmlEscape(value.toString(), true, null);
        } else {
            return value.toString();
        }
    }

}
