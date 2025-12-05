// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.rendering;

import com.fasterxml.jackson.core.Base64Variant;
import com.fasterxml.jackson.core.Base64Variants;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.json.UTF8JsonGenerator;
import com.yahoo.data.access.Inspector;
import com.yahoo.data.disclosure.DataSink;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * A sink that generates JSON from objects that implement {@link com.yahoo.data.disclosure.DataSource}.
 *
 * @author johsol
 */
class JsonGeneratorDataSink implements DataSink {

    private static final byte[] HEX_DIGITS_ASCII = "0123456789ABCDEF".getBytes(StandardCharsets.US_ASCII);
    private static final boolean RAW_AS_BASE64_DISABLED = false;

    private JsonGenerator gen;
    private boolean wantUtf8;
    boolean enableRawAsBase64;
    private final Base64Variant base64Variant = Base64Variants.getDefaultVariant();

    public JsonGeneratorDataSink(JsonGenerator gen) {
        this(gen, RAW_AS_BASE64_DISABLED);
    }

    public JsonGeneratorDataSink(JsonGenerator gen, boolean enableRawAsBase64) {
        this.gen = gen;
        this.wantUtf8 = gen instanceof UTF8JsonGenerator;
        this.enableRawAsBase64 = enableRawAsBase64;
    }

    @Override
    public void fieldName(String utf16, byte[] utf8) {
        try {
            if (utf16 != null) {
                gen.writeFieldName(utf16);
            } else {
                gen.writeFieldName(new String(utf8, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void startObject() {
        try {
            gen.writeStartObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void endObject() {
        try {
            gen.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void startArray() {
        try {
            gen.writeStartArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void endArray() {
        try {
            gen.writeEndArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void emptyValue() {
        try {
            gen.writeNull();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void booleanValue(boolean v) {
        try {
            gen.writeBoolean(v);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void longValue(long v) {
        try {
            gen.writeNumber(v);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void intValue(int v) {
        try {
            gen.writeNumber(v);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void shortValue(short v) {
        try {
            gen.writeNumber(v);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void byteValue(byte v) {
        try {
            gen.writeNumber(v);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void doubleValue(double v) {
        try {
            gen.writeNumber(v);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void floatValue(float v) {
        try {
            gen.writeNumber(v);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void stringValue(String utf16, byte[] utf8) {
        try {
            if (wantUtf8) {
               if (utf8 != null) {
                   gen.writeUTF8String(utf8, 0, utf8.length);
               } else {
                   gen.writeString(utf16);
               }
            } else {
               if (utf16 != null) {
                   gen.writeString(utf16);
               } else {
                   gen.writeString(new String(utf8, StandardCharsets.UTF_8));
               }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void dataValue(byte[] data) {
        try {
            if (enableRawAsBase64) {
                gen.writeBinary(base64Variant, data, 0, data.length);
            } else {
                if (wantUtf8) {
                    gen.writeUTF8String(toHexUtf8(data), 0, 2 + data.length * 2);
                } else {
                    gen.writeString(toHexString(data));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] toHexUtf8(byte[] data) {
        byte[] out = new byte[2 + data.length * 2];
        int p = 0;
        out[p++] = '0';
        out[p++] = 'x';
        for (byte b : data) {
            int v = b & 0xFF;
            out[p++] = HEX_DIGITS_ASCII[v >>> 4];
            out[p++] = HEX_DIGITS_ASCII[v & 0x0F];
        }
        return out;
    }

    private static String toHexString(byte[] data) {
        char[] chars = new char[2 + data.length * 2];
        int p = 0;
        chars[p++] = '0';
        chars[p++] = 'x';
        for (byte b : data) {
            int v = b & 0xFF;
            chars[p++] = (char) HEX_DIGITS_ASCII[v >>> 4];
            chars[p++] = (char) HEX_DIGITS_ASCII[v & 0x0F];
        }
        return new String(chars);
    }

    /** Write a primitive Inspector value as a JSON field name */
    void fieldNameFromPrimitive(Inspector value) {
        try {
            switch (value.type()) {
                case STRING -> gen.writeFieldName(value.asString());
                case LONG -> gen.writeFieldName(Long.toString(value.asLong()));
                case DOUBLE -> gen.writeFieldName(Double.toString(value.asDouble()));
                case BOOL -> gen.writeFieldName(value.asBool() ? "true" : "false");
                case DATA -> {
                    if (enableRawAsBase64) {
                        gen.writeFieldName(base64Variant.encode(value.asData()));
                    } else {
                        gen.writeFieldName(toHexString(value.asData()));
                    }
                }
                default -> throw new IllegalArgumentException("Cannot use " + value.type() + " as field name");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
