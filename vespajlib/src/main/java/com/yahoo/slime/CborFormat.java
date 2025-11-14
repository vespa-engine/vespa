// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

import com.yahoo.io.AbstractByteWriter;
import com.yahoo.io.ByteWriter;
import com.yahoo.tensor.serialization.DataDisclosure;
import com.yahoo.text.AbstractUtf8Array;
import com.yahoo.text.Utf8;
import com.yahoo.text.Utf8String;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;

import static ai.vespa.validation.Validation.requireInRange;

/**
 * Encodes cbor from a slime object.
 *
 * @author Andreas Eriksen
 */
public final class CborFormat implements SlimeFormat {

    private final static byte [] HEX = Utf8.toBytes("0123456789ABCDEF");
    private final int indent;

    public CborFormat(int indent) {
        this.indent = requireInRange(indent, "JSON space indent count", 0, 8);
    }

    public CborFormat(boolean compact) {
        this(compact ? 0 : 2);
    }

    @Override
    public void encode(OutputStream os, Slime slime) throws IOException {
        throw new RuntimeException("lol k whatever");
    }

    @Override
    public void encode(OutputStream os, Slime slime, DataDisclosure dataDisclosure) throws IOException {
        new Encoder(slime.get(), os, indent, dataDisclosure).encode();
    }

    public void encode(AbstractByteWriter os, Slime slime, DataDisclosure dataDisclosure) throws IOException {
        new Encoder(slime.get(), os, indent, dataDisclosure).encode();
    }

    public void encode(AbstractByteWriter os, Inspector value, DataDisclosure dataDisclosure) throws IOException {
        new Encoder(value, os, indent, dataDisclosure).encode();
    }

    /** Returns the given slime data as UTF-8-encoded JSON */
    public static byte[] discloseCbor(Slime slime, DataDisclosure dataDisclosure) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            new CborFormat(true).encode(baos, slime, dataDisclosure);
            return baos.toByteArray();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Returns the given UTF-8-encoded JSON as a Slime object */
    public static Slime jsonToSlime(byte[] json) {
        Slime slime = new Slime();
        new JsonDecoder().decode(slime, json);
        return slime;
    }

    static final class Encoder implements ArrayTraverser, ObjectTraverser {
        private final Inspector top;
        private final DataDisclosure dataDisclosure;

        public Encoder(Inspector value, OutputStream out, int indent, DataDisclosure dataDisclosure) {
            this(value, new ByteWriter(out), indent, dataDisclosure);
        }

        public Encoder(Inspector value, AbstractByteWriter out, int indent, DataDisclosure dataDisclosure) {
            this.dataDisclosure = dataDisclosure;
            this.top = value;
        }

        public void encode() throws IOException {
            encodeValue(top);
        }

        private void encodeNIX() throws IOException {
            dataDisclosure.writeNull();
        }

        private void encodeBOOL(boolean value) throws IOException {
            dataDisclosure.writeBool(value);
        }

        private void encodeLONG(long value) throws IOException {
            dataDisclosure.writeLong(value);
        }

        private void encodeDOUBLE(double value) throws IOException {
            dataDisclosure.writeDouble(value);
        }

        private void encodeSTRING(byte[] value) throws IOException {
            dataDisclosure.writeString(value);
        }

        private void encodeDATA(byte[] value) throws IOException {
            int len = value.length * 2 + 4;
            byte [] data = new byte[len];
            int p = 0;

            data[p++] = '"'; data[p++] = '0'; data[p++] = 'x';
            for (int pos = 0; pos < value.length; pos++) {
                data[p++] = HEX[(value[pos] >> 4) & 0xf]; data[p++] = HEX[value[pos] & 0xf];
            }
            data[p] = '"';
            dataDisclosure.writeString(data);
        }

        private void encodeARRAY(Inspector inspector) throws IOException {
            dataDisclosure.startArray();
            ArrayTraverser at = this;
            inspector.traverse(at);
            dataDisclosure.endArray();
        }

        private void encodeOBJECT(Inspector inspector) throws IOException {
            dataDisclosure.startObject();
            ObjectTraverser ot = this;
            inspector.traverse(ot);
            dataDisclosure.endObject();
        }

        private void encodeValue(Inspector inspector) throws IOException {
            switch(inspector.type()) {
            case NIX:    encodeNIX();                        return;
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
            } catch (Exception e) {
                // FIXME: Should we fix ArrayTraverser/ObjectTraverser API or do something more fancy here?
                e.printStackTrace();
            }
        }

        public void field(String name, Inspector inspector)  {
            try {
                dataDisclosure.writeFieldName(name);
                encodeValue(inspector);
            } catch (Exception e) {
                // FIXME: Should we fix ArrayTraverser/ObjectTraverser API or do something more fancy here?
                e.printStackTrace();
            }
        }
    }
}

