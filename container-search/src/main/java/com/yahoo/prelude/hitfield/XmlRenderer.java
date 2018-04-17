// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.hitfield;

import com.yahoo.text.Utf8;
import com.yahoo.text.XML;
import com.yahoo.data.access.Inspector;
import com.yahoo.data.access.Inspectable;
import com.yahoo.data.access.Type;
import com.yahoo.data.access.simple.Value;
import com.yahoo.data.access.slime.SlimeAdapter;
import java.nio.charset.StandardCharsets;

import java.util.Iterator;
import java.util.Map;

/**
 * Utility class for converting accessible data into the historical "prelude" xml format.
 **/
public class XmlRenderer {

    public static StringBuilder render(StringBuilder target, Inspector value) {
        new InspectorRenderer(target).renderInspector(value, 2);
        return target;
    }

    private static class InspectorRenderer {

        private final StringBuilder renderTarget;

        InspectorRenderer(StringBuilder target) {
            this.renderTarget = target;
        }

        void renderInspector(Inspector value, int nestingLevel) {
            if (value.type() == Type.ARRAY) {
                renderMapOrArray(value, nestingLevel);
            } else if (value.type() == Type.OBJECT) {
                renderStruct(value, nestingLevel);
            } else if (value.type() == Type.STRING) {
                renderTarget.append(XML.xmlEscape(value.asString(), false));
            } else if (value.type() == Type.LONG) {
                long l = value.asLong();
                renderTarget.append(String.valueOf(l));
            } else if (value.type() == Type.DOUBLE) {
                double d = value.asDouble();
                renderTarget.append(String.valueOf(d));
            } else if (value.type() == Type.BOOL) {
                boolean b = value.asBool();
                renderTarget.append(b ? "true" : "false");
            } else if (value.type() == Type.DATA) {
                byte[] data = value.asData();
                renderTarget.append("<data length=\"").append(data.length);
                renderTarget.append("\" encoding=\"hex\">");
                for (int i = 0; i < data.length; i++) {
                    for (int sh = 4; sh >= 0; sh -= 4) {
                        int val = (data[i] >> sh) & 0xF;
                        char hexdigit = (val < 10) ? ((char)('0' + val)) : ((char)('A' + val - 10));
                        renderTarget.append(hexdigit);
                    }
                }
                renderTarget.append("</data>");
            }
        }

        private void renderMapItem(Inspector object, int nestingLevel) {
            renderTarget.append('\n');
            indent(nestingLevel);
            renderTarget.append("<item><key>");
            renderInspector(object.field("key"), nestingLevel);
            renderTarget.append("</key><value>");
            renderInspector(object.field("value"), nestingLevel);
            renderTarget.append("</value></item>");
        }

        private void renderStructure(Inspector structure, int nestingLevel) {
            for (Map.Entry<String,Inspector> entry : structure.fields()) {
                String key = entry.getKey();
                Inspector value = entry.getValue();
                renderTarget.append('\n');
                indent(nestingLevel);
                renderTarget.append("<struct-field name=\"").append(key).append("\">");
                renderInspector(value, nestingLevel);
                renderTarget.append("</struct-field>");
            }
            renderTarget.append('\n');
        }

        private void renderStruct(Inspector object, int nestingLevel) {
            renderStructure(object, nestingLevel + 1);
            indent(nestingLevel);
        }

        private void indent(int nestingLevel) {
            for (int i = 0; i < nestingLevel; ++i) {
                renderTarget.append("  ");
            }
        }

        private void renderMap(Inspector sequence, int nestingLevel) {
            int limit = sequence.entryCount();
            if (limit == 0) return;
            for (int i = 0; i < limit; ++i)
                renderMapItem(sequence.entry(i), nestingLevel);
            renderTarget.append("\n");
        }

        /** Returns true if the given array represents a map - a list of pairs called "key" and "value" */
        private boolean isMap(Inspector array) {
            Inspector firstObject = array.entry(0);
            if (firstObject.type() != Type.OBJECT) return false;
            if (firstObject.fieldCount() != 2) return false;
            if (! firstObject.field("key").valid()) return false;
            if (! firstObject.field("value").valid()) return false;
            return true;
        }

        /**
         * Returns true if the given array represents a weighted set,
         * as a list of pairs called "item" and "weight"
         **/
        private boolean isWeightedSetObjects(Inspector array) {
            Inspector firstObject = array.entry(0);
            if (firstObject.type() != Type.OBJECT) return false;
            if (firstObject.fieldCount() != 2) return false;
            if (! firstObject.field("item").valid()) return false;
            if (! firstObject.field("weight").valid()) return false;
            return true;
        }

        /**
         * Returns true if the given array represents a weighted set,
         * as a list of tuples
         **/
        private boolean isWeightedSetArrays(Inspector array) {
            Inspector firstObject = array.entry(0);
            if (firstObject.type() != Type.ARRAY) return false;
            if (firstObject.entryCount() != 2) return false;
            return true;
        }

        private void renderMapOrArray(Inspector sequence, int nestingLevel)
        {
            if (sequence.entryCount() == 0) return;
            if (isMap(sequence)) {
                renderMap(sequence, nestingLevel + 1);
            } else if (isWeightedSetArrays(sequence)) {
                renderWeightedSet(sequence, nestingLevel + 1, true);
            } else if (isWeightedSetObjects(sequence)) {
                renderWeightedSet(sequence, nestingLevel + 1, false);
            } else {
                renderArray(sequence, nestingLevel + 1);
            }
            indent(nestingLevel);
        }

        private void renderWeightedSet(Inspector seq, int nestingLevel, boolean nestedarray)
        {
            int limit = seq.entryCount();
            renderTarget.append('\n');
            for (int i = 0; i < limit; ++i) {
                Inspector value  = nestedarray ? seq.entry(i).entry(0) : seq.entry(i).field("item");
                Inspector weight = nestedarray ? seq.entry(i).entry(1) : seq.entry(i).field("weight");
                long lw = 0;
                double dw = 0;
                if (weight.type() == Type.LONG) {
                    lw = weight.asLong();
                    dw = (double)lw;
                }
                if (weight.type() == Type.DOUBLE) {
                    dw = weight.asDouble();
                    lw = (long)dw;
                }
                indent(nestingLevel);
                renderTarget.append("<item weight=\"");
                if (dw == (double)lw || weight.type() == Type.LONG) {
                    renderTarget.append(lw);
                } else {
                    renderTarget.append(dw);
                }
                renderTarget.append("\">");
                renderInspector(value, nestingLevel);
                renderTarget.append("</item>\n");
            }
        }

        private void renderArray(Inspector seq, int nestingLevel) {
            int limit = seq.entryCount();
            if (limit == 0) return;
            renderTarget.append('\n');
            for (int i = 0; i < limit; ++i) {
                Inspector value = seq.entry(i);
                indent(nestingLevel);
                renderTarget.append("<item>");
                renderInspector(value, nestingLevel);
                renderTarget.append("</item>\n");
            }
        }

    }

}
