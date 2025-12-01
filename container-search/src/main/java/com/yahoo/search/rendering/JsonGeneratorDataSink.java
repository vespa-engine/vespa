// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.rendering;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.json.UTF8JsonGenerator;
import com.yahoo.data.disclosure.DataSink;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * A sink that generates JSON from objects that implement {@link com.yahoo.data.disclosure.DataSource}.
 *
 * @author johsol
 */
record JsonGeneratorDataSink(JsonGenerator gen) implements DataSink {

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
            if (utf16 != null) {
                gen.writeString(utf16);
            } else if (gen instanceof UTF8JsonGenerator utf8Gen) {
                utf8Gen.writeUTF8String(utf8, 0, utf8.length);
            } else {
                gen.writeString(new String(utf8, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void dataValue(byte[] data) {
       throw new UnsupportedOperationException("Not implemented");
    }
}
