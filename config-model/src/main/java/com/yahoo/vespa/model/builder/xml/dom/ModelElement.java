// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.text.XML;
import com.yahoo.vespa.model.utils.Duration;

import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * A w3c Element wrapper whith a better API.
 *
 * Author unknown.
 */
public class ModelElement {

    private final Element xml;

    public ModelElement(Element xml) {
        this.xml = xml;
        if (xml == null) {
            throw new NullPointerException("Can not create ModelElement with null element");
        }
        if (xml.getNodeName() == null) {
            throw new NullPointerException("Can not create ModelElement with unnamed element");
        }
    }

    public Element getXml() {
        return xml;
    }

    /**
     * If not found, return null.
     */
    public ModelElement getChild(String name) {
        Element e = XML.getChild(xml, name);

        if (e != null) {
            return new ModelElement(e);
        }

        return null;
    }

    public ModelElement getChildByPath(String path) {
        StringTokenizer tokenizer = new StringTokenizer(path, ".");
        ModelElement curElem = this;
        while (tokenizer.hasMoreTokens() && curElem != null) {
            String pathElem = tokenizer.nextToken();
            ModelElement child = curElem.getChild(pathElem);
            if (!tokenizer.hasMoreTokens()) {
                if (child != null) {
                    return child;
                }
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
            ModelElement child = curElem.getChild(pathElem);
            if (!tokenizer.hasMoreTokens()) {
                String attr = curElem.getStringAttribute(pathElem);
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
        if (child == null) {
            return null;
        }
        return Long.parseLong(child.trim());
    }

    public Integer childAsInteger(String path) {
        String child = childAsString(path);
        if (child == null) {
            return null;
        }
        return Integer.parseInt(child.trim());
    }

    public Double childAsDouble(String path) {
        String child = childAsString(path);
        if (child == null) {
            return null;
        }
        return Double.parseDouble(child.trim());
    }

    public Boolean childAsBoolean(String path) {
        String child = childAsString(path);
        if (child == null) {
            return null;
        }
        return Boolean.parseBoolean(child.trim());
    }

    public Duration childAsDuration(String path) {
        String child = childAsString(path);
        if (child == null) {
            return null;
        }
        return new Duration(child);
    }

    /** Returns the given attribute or throws IllegalArgumentException if not present */
    public int requiredIntegerAttribute(String name) {
        if (getStringAttribute(name) == null)
            throw new IllegalArgumentException("Required attribute '" + name + "' is missing");
        return getIntegerAttribute(name, null);
    }

    /** Returns the value of this attribute or null if not present */
    public Integer getIntegerAttribute(String name) {
        return getIntegerAttribute(name, null);
    }

    public Integer getIntegerAttribute(String name, Integer defaultValue) {
        String value = getStringAttribute(name);
        if (value == null) {
            return defaultValue;
        }
        return (int) BinaryUnit.valueOf(value);
    }

    public boolean getBooleanAttribute(String name) {
        return getBooleanAttribute(name, false);
    }

    public boolean getBooleanAttribute(String name, boolean defaultValue) {
        String value = getStringAttribute(name);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    public Long getLongAttribute(String name) {
        String value = getStringAttribute(name);
        if (value == null) {
            return null;
        }
        return (long) BinaryUnit.valueOf(value);
    }

    public Double getDoubleAttribute(String name) {
        String value = getStringAttribute(name);
        if (value == null) {
            return null;
        }
        return Double.parseDouble(value);
    }

    public String getStringAttribute(String name) {
        if (!xml.hasAttribute(name)) {
            return null;
        }

        return xml.getAttribute(name);
    }

    public List<ModelElement> subElements(String name) {
        List<Element> elements = XML.getChildren(xml, name);

        List<ModelElement> helpers = new ArrayList<>();
        for (Element e : elements) {
            helpers.add(new ModelElement(e));
        }

        return helpers;
    }
    
    @Override
    public String toString() {
        return xml.getNodeName();
    }
}
