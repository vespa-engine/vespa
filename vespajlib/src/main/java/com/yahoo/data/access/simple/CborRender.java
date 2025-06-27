// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.data.access.simple;

import com.yahoo.data.access.ArrayTraverser;
import com.yahoo.data.access.Inspector;
import com.yahoo.data.access.ObjectTraverser;
import com.yahoo.tensor.serialization.DataDisclosure;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Serializes an inspectable object to the data disclosure thing
 *
 * @author andreer
 */
public final class CborRender {

    private static DataDisclosure dataDisclosure;

    public static void render(Inspector data, DataDisclosure dataDisclosure) throws IOException {
        CborRender.dataDisclosure = dataDisclosure;
        new StringEncoder().encode(data);
    }

    public static class StringEncoder implements ArrayTraverser, ObjectTraverser {

        public void encode(Inspector top) throws IOException {
            encodeValue(top);
        }

        protected void encodeEMPTY() throws IOException {
            dataDisclosure.writeNull();
        }

        protected void encodeBOOL(boolean value) throws IOException {
            dataDisclosure.writeBool(value);
        }

        protected void encodeLONG(long value) throws IOException {
            dataDisclosure.writeLong(value);
        }

        protected void encodeDOUBLE(double value) throws IOException {
            dataDisclosure.writeDouble(value);
        }

        static final char[] hex = "0123456789ABCDEF".toCharArray();

        protected void encodeSTRING(byte[] value) throws IOException {
            // TODO: Can we find out if it's already utf-8 or not?
            dataDisclosure.writeString(value);
        }

        protected void encodeDATA(byte[] value) throws IOException {
            // TODO: consider using binary string for this instead - semantic difference but large efficiency gain
            // TODO: actually we can use binary string but tag 23?
            StringBuilder out = new StringBuilder();
            out.append('"');
            out.append("0x");
            for (byte b : value) {
                out.append(hex[(b >> 4) & 0xf]);
                out.append(hex[b & 0xf]);
            }
            out.append('"');
            encodeSTRING(out.toString().getBytes(StandardCharsets.UTF_8));
        }

        protected void encodeARRAY(Inspector inspector) throws IOException {
            dataDisclosure.startArray();
            ArrayTraverser at = this;
            inspector.traverse(at);
            dataDisclosure.endArray();
        }

        protected void encodeOBJECT(Inspector inspector) throws IOException {
            dataDisclosure.startObject();
            ObjectTraverser ot = this;
            inspector.traverse(ot);
            dataDisclosure.endObject();
        }

        private void encodeValue(Inspector inspector) throws IOException {
            switch(inspector.type()) {
            case EMPTY:  encodeEMPTY();                      return;
            case BOOL:   encodeBOOL(inspector.asBool());     return;
            case LONG:   encodeLONG(inspector.asLong());     return;
            case DOUBLE: encodeDOUBLE(inspector.asDouble()); return;
            case STRING: encodeSTRING(inspector.asUtf8());   return;
            case DATA:   encodeDATA(inspector.asData());     return;
            case ARRAY:  encodeARRAY(inspector);             return;
            case OBJECT: encodeOBJECT(inspector);            return;
            }
            assert false : "Should not be reached";
        }

        public void entry(int idx, Inspector inspector) {
            try {
                encodeValue(inspector);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public void field(String name, Inspector inspector) {
            try {
                dataDisclosure.writeFieldName(name);
                encodeValue(inspector);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        /** Returns the target this is encoding values to */
        public DataDisclosure target() { return dataDisclosure; }

    }

}
