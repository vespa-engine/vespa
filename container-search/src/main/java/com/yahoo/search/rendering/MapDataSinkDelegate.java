package com.yahoo.search.rendering;

import com.yahoo.data.disclosure.DataSink;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * This class expects that the structure is checked to be a conversion to map.
 * This requires the input to be an array of objects where the objects contain
 * fields key and value that maps to the value. For instance, input=[{key:"id", value:3}, ]
 * will be mapped to output: {id: 3}.
 */
class MapDataSinkDelegate extends DataSinkDelegate {

    private boolean nextIsField = true;

    public MapDataSinkDelegate(DataSink delegate) {
        super(delegate);
    }

    @Override
    public void startObject() { /* no-op */ }

    @Override
    public void endObject() { /* no-op */ }

    @Override
    public void startArray() {
        delegate.startObject();
    }

    @Override
    public void endArray() {
        delegate.endObject();
    }

    @Override
    public void fieldName(String utf16, byte[] utf8) {
        if (utf8 != null) {
            this.fieldName(utf8);
        } else {
            this.fieldName(utf16);
        }
    }

    @Override
    public void fieldName(String utf16) {
        nextIsField = Objects.equals(utf16, "key");
    }

    @Override
    public void fieldName(byte[] utf8) {
        nextIsField = Arrays.equals(utf8, "key".getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void stringValue(String utf16, byte[] utf8) {
        if (utf8 != null) {
            this.stringValue(utf8);
        } else {
            this.stringValue(utf16);
        }
    }

    @Override
    public void stringValue(String utf16) {
        if (nextIsField) {
            delegate.fieldName(utf16);
        } else {
            delegate.stringValue(utf16);
        }
    }

    @Override
    public void stringValue(byte[] utf8) {
        if (nextIsField) {
            delegate.fieldName(utf8);
        } else {
            delegate.stringValue(utf8);
        }
    }
}
