package com.yahoo.search.rendering;

import com.yahoo.data.disclosure.DataSink;

import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation sends all to the delegate.
 */
class DataSinkDelegate implements DataSink {

    protected DataSink delegate;
    private List<DataSink> delegates = new ArrayList<>();

    public DataSinkDelegate(DataSink delegate) {
        this.delegate = delegate;
        delegates.add(this);
    }

    void pushDelegate(DataSink delegate) {
        this.delegates.add(delegate);
        this.delegate = delegate;
    }

    void popDelegate() {
        this.delegates.remove(this.delegate);
        this.delegate = delegates.remove(delegates.size() - 1);
    }

    @Override public void fieldName(String utf16, byte[] utf8) { delegate.fieldName(utf16, utf8); }
    @Override public void fieldName(String utf16) { delegate.fieldName(utf16); }
    @Override public void fieldName(byte[] utf8) { delegate.fieldName(utf8); }
    @Override public void startObject() { delegate.startObject();}
    @Override public void endObject() { delegate.endObject(); }
    @Override public void startArray() { delegate.startArray(); }
    @Override public void endArray() { delegate.endArray(); }
    @Override public void emptyValue() { delegate.emptyValue(); }
    @Override public void booleanValue(boolean v) { delegate.booleanValue(v); }
    @Override public void longValue(long v) { delegate.longValue(v); }
    @Override public void intValue(int v) { delegate.intValue(v); }
    @Override public void shortValue(short v) { delegate.shortValue(v); }
    @Override public void byteValue(byte v) { delegate.byteValue(v); }
    @Override public void doubleValue(double v) { delegate.doubleValue(v); }
    @Override public void floatValue(float v) { delegate.floatValue(v); }
    @Override public void stringValue(String utf16, byte[] utf8) { delegate.stringValue(utf16, utf8); }
    @Override public void stringValue(String utf16) { delegate.stringValue(utf16); }
    @Override public void stringValue(byte[] utf8) { delegate.stringValue(utf8); }
    @Override public void dataValue(byte[] data) { delegate.dataValue(data); }
}
