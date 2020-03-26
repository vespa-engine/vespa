// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.text.XML;
import com.yahoo.vespa.model.utils.Duration;

import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * A w3c Element wrapper with a better API.
 *
 * Author unknown.
 */
public class ModelElement {

    private final Element xml;

    public ModelElement(Element xml) {
        if (xml == null) throw new NullPointerException("Can not create ModelElement with null element");
        if (xml.getNodeName() == null) throw new NullPointerException("Can not create ModelElement with unnamed element");
        this.xml = xml;
    }

    public Element getXml() { return xml; }

    /** Returns the child with the given name, or null if none. */
    public ModelElement child(String name) {
        Element e = XML.getChild(xml, name);
        if (e == null) return null;
        return new ModelElement(e);
    }

    /** If not found, return empty list. */
    public List<ModelElement> children(String name) {
        List<Element> e = XML.getChildren(xml, name);

        List<ModelElement> list = new ArrayList<>();
        e.forEach(element -> list.add(new ModelElement(element)));
        return list;
    }

    public ModelElement childByPath(String path) {
        StringTokenizer tokenizer = new StringTokenizer(path, ".");
        ModelElement curElem = this;
        while (tokenizer.hasMoreTokens() && curElem != null) {
            String pathElem = tokenizer.nextToken();
            ModelElement child = curElem.child(pathElem);
            if (!tokenizer.hasMoreTokens()) {
                if (child != null)
                    return child;
            }
            curElem = child;
        }
        return null;
    }

    public String childAsString(String path) {
        StringTokenizer tokenizer = new StringTokenizer(path, ".");
        ModelElement curElem = this;
        while (tokenizer.hasMoreTokens() && curElem != null) {
            String pathElem = tokenizer.nextToken();
            ModelElement child = curElem.child(pathElem);
            if (!tokenizer.hasMoreTokens()) {
                String attr = curElem.stringAttribute(pathElem);
                if (attr != null) {
                    return attr;
                } else if (child != null) {
                    return child.asString();
                }
            }
            curElem = child;
        }
        return null;
    }

    public String asString() {
        return xml.getFirstChild().getTextContent();
    }

    public double asDouble() {
        return Double.parseDouble(asString());
    }

    public long asLong() {
        return (long) BinaryUnit.valueOf(asString());
    }

    public Duration asDuration() {
        return new Duration(asString());
    }

    public Long childAsLong(String path) {
        String child = childAsString(path);
        if (child == null) return null;
        return Long.parseLong(child.trim());
    }

    public Integer childAsInteger(String path) {
        String child = childAsString(path);
        if (child == null) return null;
        return Integer.parseInt(child.trim());
    }

    public Double childAsDouble(String path) {
        String child = childAsString(path);
        if (child == null) return null;
        return Double.parseDouble(child.trim());
    }

    public Boolean childAsBoolean(String path) {
        String child = childAsString(path);
        if (child == null) return null;
        return Boolean.parseBoolean(child.trim());
    }

    public Duration childAsDuration(String path) {
        String child = childAsString(path);
        if (child == null) return null;
        return new Duration(child);
    }

    /** Returns the given attribute or throws IllegalArgumentException if not present */
    public int requiredIntegerAttribute(String name) {
        if (stringAttribute(name) == null)
            throw new IllegalArgumentException("Required attribute '" + name + "' is missing");
        return integerAttribute(name, null);
    }

    /** Returns the value of this attribute or null if not present */
    public Integer integerAttribute(String name) {
        return integerAttribute(name, null);
    }

    public Integer integerAttribute(String name, Integer defaultValue) {
        String value = stringAttribute(name);
        if (value == null) return defaultValue;
        return (int) BinaryUnit.valueOf(value);
    }

    public boolean booleanAttribute(String name) {
        return booleanAttribute(name, false);
    }

    public boolean booleanAttribute(String name, boolean defaultValue) {
        String value = stringAttribute(name);
        if (value == null) return defaultValue;
        return Boolean.parseBoolean(value);
    }

    public Long longAttribute(String name) {
        String value = stringAttribute(name);
        if (value == null) return null;
        return (long) BinaryUnit.valueOf(value);
    }

    public Double doubleAttribute(String name) {
        String value = stringAttribute(name);
        if (value == null) return null;
        return Double.parseDouble(value);
    }

    /** Returns the given attribute or throws IllegalArgumentException if not present */
    public double requiredDoubleAttribute(String name) {
        if (stringAttribute(name) == null)
            throw new IllegalArgumentException("Required attribute '" + name + "' is missing");
        return doubleAttribute(name);
    }

    /** Returns the content of the attribute with the given name, or null if none */
    public String stringAttribute(String name) {
        return stringAttribute(name, null);
    }

    /** Returns the content of the attribute with the given name, or the default value if none */
    public String stringAttribute(String name, String defaultValue) {
        if ( ! xml.hasAttribute(name)) return defaultValue;
        return xml.getAttribute(name);
    }

    /** Returns the content of the attribute with the given name or throws IllegalArgumentException if not present */
    public String requiredStringAttribute(String name) {
        if (stringAttribute(name) == null)
            throw new IllegalArgumentException("Required attribute '" + name + "' is missing");
        return stringAttribute(name);
    }


    public List<ModelElement> subElements(String name) {
        List<Element> elements = XML.getChildren(xml, name);

        List<ModelElement> helpers = new ArrayList<>();
        for (Element e : elements)
            helpers.add(new ModelElement(e));
        return helpers;
    }
    
    @Override
    public String toString() {
        return xml.getNodeName();
    }

}
