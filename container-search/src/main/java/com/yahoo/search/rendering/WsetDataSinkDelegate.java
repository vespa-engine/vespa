package com.yahoo.search.rendering;

import com.yahoo.data.disclosure.DataSink;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * Converts a weighted set represented as an array of objects with {@code item} and
 * {@code weight} fields into a JSON object mapping {@code item} to {@code weight}.
 */
class WsetDataSinkDelegate extends DataSinkDelegate {

    private boolean nextIsField = true;

    public WsetDataSinkDelegate(DataSink delegate) {
        super(delegate);
    }

    @Override
    public void startObject() { /* no-op */ }

    @Override
    public void endObject() { /* no-op */ }

    @Override
    public void startArray() { delegate.startObject(); }

    @Override
    public void endArray() { delegate.endObject(); }

    @Override
    public void fieldName(String utf16, byte[] utf8) {
        if (utf8 != null) {
            fieldName(utf8);
        } else {
            fieldName(utf16);
        }
    }

    @Override
    public void fieldName(String utf16) {
        nextIsField = Objects.equals(utf16, "item");
    }

    @Override
    public void fieldName(byte[] utf8) {
        nextIsField = Arrays.equals(utf8, "item".getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void stringValue(String utf16, byte[] utf8) {
        if (utf8 != null) {
            stringValue(utf8);
        } else {
            stringValue(utf16);
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

    @Override
    public void longValue(long v) {
        if (nextIsField) {
            delegate.fieldName(Long.toString(v));
        } else {
            delegate.longValue(v);
        }
    }

    @Override
    public void doubleValue(double v) {
        if (nextIsField) {
            delegate.fieldName(Double.toString(v));
        } else {
            delegate.doubleValue(v);
        }
    }

    @Override
    public void booleanValue(boolean v) {
        if (nextIsField) {
            delegate.fieldName(v ? "true" : "false");
        } else {
            delegate.booleanValue(v);
        }
    }
}
