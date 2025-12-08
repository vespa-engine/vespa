// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.rendering;

import com.yahoo.data.disclosure.DataSink;

/**
 * Wraps a DataSink to convert non-finite double/float values (NaN, Infinity, -Infinity) to null,
 * to ensure the output is the same as from JsonRender. The Jackson generator by default produces
 * the strings "NaN", "-Infinity" and "Infinity" for such values.
 *
 * @author andreer
 */
public record NonFiniteToNullDataSink(DataSink delegate) implements DataSink {

    @Override
    public void doubleValue(double v) {
        if (Double.isFinite(v)) delegate.doubleValue(v); else delegate.emptyValue();
    }

    @Override
    public void floatValue(float v) {
        if (Float.isFinite(v)) delegate.floatValue(v); else delegate.emptyValue();
    }

    @Override public void fieldName(String utf16, byte[] utf8) { delegate.fieldName(utf16, utf8); }
    @Override public void startObject() { delegate.startObject(); }
    @Override public void endObject() { delegate.endObject(); }
    @Override public void startArray() { delegate.startArray(); }
    @Override public void endArray() { delegate.endArray(); }
    @Override public void emptyValue() { delegate.emptyValue(); }
    @Override public void booleanValue(boolean v) { delegate.booleanValue(v); }
    @Override public void longValue(long v) { delegate.longValue(v); }
    @Override public void stringValue(String utf16, byte[] utf8) { delegate.stringValue(utf16, utf8); }
    @Override public void dataValue(byte[] data) { delegate.dataValue(data); }
}
