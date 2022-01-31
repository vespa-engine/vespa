// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.result;

import com.yahoo.data.access.Inspector;
import com.yahoo.data.access.Inspectable;
import com.yahoo.data.access.Type;
import com.yahoo.data.JsonProducer;
import com.yahoo.data.XmlProducer;
import com.yahoo.data.access.simple.JsonRender;

/**
 * A wrapper for structured data representing an array of position values.
 */
public class PositionsData implements Inspectable, JsonProducer, XmlProducer {

    private final Inspector value;

    public PositionsData(Inspector value) {
        this.value = value;
        if (value.type() != Type.OBJECT && value.type() != Type.ARRAY) {
            throw new IllegalArgumentException("PositionsData expects a position or an array of positions, got: "+value);
        }
    }

    @Override
    public Inspector inspect() {
        return value;
    }

    @Override
    public String toString() {
        return toJson();
    }

    @Override
    public StringBuilder writeJson(StringBuilder target) {
        return JsonRender.render(value, target, true);
    }

    @Override
    public StringBuilder writeXML(StringBuilder target) {
        if (value.type() == Type.OBJECT) {
            writeXML(value.inspect(), target);
        } else {
            for (int i = 0; i < value.entryCount(); i++) {
                Inspector pos = value.entry(i);
                writeXML(pos, target);
            }
        }
        return target;
    }

    private static void writeXML(Inspector pos, StringBuilder target) {
        target.append("<position ");
        for (java.util.Map.Entry<String, Inspector> entry : pos.fields()) {
            Inspector v = entry.getValue();
            if (v.type() == Type.STRING) {
                target.append(entry.getKey());
                target.append("=\"");
                target.append(entry.getValue().asString());
                target.append("\" ");
            }
            if (v.type() == Type.LONG) {
                target.append(entry.getKey());
                target.append("=\"");
                target.append(entry.getValue().asLong());
                target.append("\" ");
            }
        }
        target.append("/>");
    }

}
