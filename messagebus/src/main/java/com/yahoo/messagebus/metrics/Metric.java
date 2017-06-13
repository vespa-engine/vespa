// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.metrics;

import com.yahoo.text.XMLWriter;
import com.yahoo.text.Utf8String;

import java.io.Writer;

/**
 * @author thomasg
 */
public abstract class Metric {
    String name;
    String xmlTagName = null;

    public Metric(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String toHTML() {
        return toString();
    }

    public String getXmlTagName() {
        return xmlTagName;
    }

    public void setXmlTagName(String newName) {
        xmlTagName = newName;
    }

    static private final Utf8String attrName = new Utf8String("name");

    public void renderXmlName(XMLWriter writer) {
        if (xmlTagName != null) {
            writer.openTag(xmlTagName);
            writer.attribute(attrName, name);
        } else {
            writer.openTag(name);
        }
    }

    public abstract void toXML(XMLWriter writer);
}
