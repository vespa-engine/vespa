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
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Encodes json from a slime object.
 *
 * @author Ulf Lilleengen
 */
public final class JsonFormat implements SlimeFormat {

    private final static byte [] HEX = Utf8.toBytes("0123456789ABCDEF");
    private final int indent;

    public JsonFormat(int indent) {
        this.indent = requireInRange(indent, "JSON space indent count", 0, 8);
    }

    public JsonFormat(boolean compact) {
        this(compact ? 0 : 2);
    }

    @Override
    public void encode(OutputStream os, Slime slime) throws IOException {
        new Encoder(slime.get(), os, indent).encode();
    }

    @Override
    public void encode(OutputStream os, Slime slime, DataDisclosure dataDisclosure) throws IOException {
        throw new RuntimeException("omg no way dude");
    }

    public void encode(OutputStream os, Inspector value) throws IOException {
        new Encoder(value, os, indent).encode();
    }

    public void encode(AbstractByteWriter os, Slime slime) throws IOException {
        new Encoder(slime.get(), os, indent).encode();
    }

    public void encode(AbstractByteWriter os, Inspector value) throws IOException {
        new Encoder(value, os, indent).encode();
    }

    /** Returns the given slime data as UTF-8-encoded JSON */
    public static byte[] toJsonBytes(Slime slime) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            new JsonFormat(true).encode(baos, slime);
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
        private final AbstractByteWriter out;
        private final byte[] indent;
        private boolean head = true;
        private int level = 0;
        final static AbstractUtf8Array NULL=new Utf8String("null");
        final static AbstractUtf8Array FALSE=new Utf8String("false");
        final static AbstractUtf8Array TRUE=new Utf8String("true");

        public Encoder(Inspector value, OutputStream out, int indent) {
            this(value, new ByteWriter(out), indent);
        }

        public Encoder(Inspector value, AbstractByteWriter out, int indent) {
            this.top = value;
            this.out = out;
            this.indent = indent == 0 ? null : " ".repeat(indent).getBytes(UTF_8);
        }

        public void encode() throws IOException {
            encodeValue(top);
            if (indent != null) {
                out.append((byte) '\n');
            }
            out.flush();
        }

        private void encodeNIX() throws IOException {
            out.write(NULL);
        }

        private void encodeBOOL(boolean value) throws IOException {
            out.write(value ? TRUE : FALSE);
        }

        private void encodeLONG(long value) throws IOException {
            out.write(value);
        }

        private void encodeDOUBLE(double value) throws IOException {
            if (Double.isFinite(value)) {
                out.write(String.valueOf(value));
            } else {
                out.write(NULL);
            }
        }

        private void encodeSTRING(byte[] value) throws IOException {

            byte [] data = new byte[value.length * 6 + 2];
            int len = 2;
            int p = 0;
            data[p++] = '"';
            for (int pos = 0; pos < value.length; pos++) {
                byte c = value[pos];
                switch (c) {
                case '"':  data[p++] = '\\'; data[p++] = '"';  len += 2; break;
                case '\\': data[p++] = '\\'; data[p++] = '\\'; len += 2; break;
                case '\b': data[p++] = '\\'; data[p++] = 'b';  len += 2; break;
                case '\f': data[p++] = '\\'; data[p++] = 'f';  len += 2; break;
                case '\n': data[p++] = '\\'; data[p++] = 'n';  len += 2; break;
                case '\r': data[p++] = '\\'; data[p++] = 'r';  len += 2; break;
                case '\t': data[p++] = '\\'; data[p++] = 't';  len += 2; break;
                default:
                    if (c > 0x1f || c < 0) {
                        data[p++] = c;
                        len++;
                    } else { // requires escaping according to RFC 4627
                        data[p++] = '\\'; data[p++] = 'u'; data[p++] = '0'; data[p++] = '0';
                        data[p++] = HEX[(c >> 4) & 0xf]; data[p++] = HEX[c & 0xf];
                        len += 6;
                    }
                }
            }
            data[p] = '"';
            out.append(data, 0, len);
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
            out.append(data, 0, len);
        }

        private void encodeARRAY(Inspector inspector) throws IOException {
            openScope((byte)'[');
            ArrayTraverser at = this;
            inspector.traverse(at);
            closeScope((byte)']');
        }

        private void encodeOBJECT(Inspector inspector) throws IOException {
            openScope((byte)'{');
            ObjectTraverser ot = this;
            inspector.traverse(ot);
            closeScope((byte) '}');
        }

        private void openScope(byte opener) throws IOException {
            out.append(opener);
            level++;
            head = true;
        }

        private void closeScope(byte closer) throws IOException {
            level--;
            separate(false);
            out.append(closer);
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

        private void separate(boolean useComma) throws IOException {
            boolean newline = ! head || useComma;
            if ( ! head && useComma) {
                out.append((byte)',');
            } else {
                head = false;
            }
            if (indent != null) {
                if (newline) {
                    out.append((byte) '\n');
                    for (int lvl = 0; lvl < level; lvl++) { out.append(indent); }
                } else {
                    out.append((byte) ' ');
                }
            }
        }

        public void entry(int idx, Inspector inspector) {
            try {
                separate(true);
                encodeValue(inspector);
            } catch (Exception e) {
                // FIXME: Should we fix ArrayTraverser/ObjectTraverser API or do something more fancy here?
                e.printStackTrace();
            }
        }

        public void field(String name, Inspector inspector)  {
            try {
                separate(true);
                encodeSTRING(Utf8Codec.encode(name));
                out.append((byte)':');
                if (indent != null)
                    out.append((byte) ' ');
                encodeValue(inspector);
            } catch (Exception e) {
                // FIXME: Should we fix ArrayTraverser/ObjectTraverser API or do something more fancy here?
                e.printStackTrace();
            }
        }
    }
}

