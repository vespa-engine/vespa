package com.yahoo.search.rendering;

import com.yahoo.data.access.Inspector;
import com.yahoo.data.access.Type;
import com.yahoo.data.disclosure.DataSink;

class InspectorDataSinkDelegate extends DataSinkDelegate {

    private final ConversionSettings settings;

    public InspectorDataSinkDelegate(DataSink delegate, ConversionSettings settings) {
        super(delegate);
        this.settings = settings;
    }

    /** Emit an inspector with map/wset conversion controlled by settings. */
    void emitInspector(Inspector inspector) {
        emitInspector(inspector, this, true);
    }

    private void emitInspector(Inspector inspector, DataSink sink, boolean topLevel) {
        switch (inspector.type()) {
            case ARRAY -> emitArray(inspector, sink, topLevel);
            case OBJECT -> emitObject(inspector, sink, topLevel);
            default -> inspector.emit(sink);
        }
    }

    private void emitArray(Inspector inspector, DataSink sink, boolean topLevel) {
        boolean allowDeep = topLevel || settings.convertDeep();
        if (allowDeep) {
            if (isConvertibleToMap(inspector, topLevel)) {
                emitMapArray(inspector, baseSink(sink));
                return;
            }
            if (isConvertibleToWset(inspector, topLevel)) {
                emitWsetArray(inspector, baseSink(sink));
                return;
            }
        }
        if (!allowDeep) {
            inspector.emit(sink);
            return;
        }
        sink.startArray();
        for (var entry : inspector.entries()) {
            emitInspector(entry, sink, false);
        }
        sink.endArray();
    }

    private void emitMapArray(Inspector inspector, DataSink sink) {
        sink.startObject();
        for (var entry : inspector.entries()) {
            Inspector key = entry.field("key");
            Inspector value = entry.field("value");
            sink.fieldName(toFieldName(key));
            emitInspector(value, sink, settings.convertDeep());
        }
        sink.endObject();
    }

    private void emitWsetArray(Inspector inspector, DataSink sink) {
        sink.startObject();
        for (var entry : inspector.entries()) {
            Inspector item = entry.field("item");
            Inspector weight = entry.field("weight");
            sink.fieldName(toFieldName(item));
            sink.longValue(weight.asLong());
        }
        sink.endObject();
    }

    private void emitObject(Inspector inspector, DataSink sink, boolean topLevel) {
        if (!topLevel && !settings.convertDeep()) {
            inspector.emit(sink);
            return;
        }
        sink.startObject();
        for (var field : inspector.fields()) {
            sink.fieldName(field.getKey());
            emitInspector(field.getValue(), sink, false);
        }
        sink.endObject();
    }

    private boolean isConvertibleToMap(Inspector inspector, boolean topLevel) {
        if (!(topLevel || settings.jsonDeepMaps)) return false;
        if (inspector.type() != Type.ARRAY) return false;
        for (var entry : inspector.entries()) {
            if (entry.type() != Type.OBJECT || entry.fieldCount() != 2) return false;
            Inspector key = entry.field("key");
            Inspector value = entry.field("value");
            if (!key.valid() || !value.valid()) return false;
            if (key.type() != Type.STRING && !settings.jsonMapsAll) return false;
        }
        return true;
    }

    private boolean isConvertibleToWset(Inspector inspector, boolean topLevel) {
        if (!settings.jsonWsets) return false;
        if (!(topLevel || settings.jsonWsets)) return false;
        if (inspector.type() != Type.ARRAY) return false;
        for (var entry : inspector.entries()) {
            if (entry.type() != Type.OBJECT || entry.fieldCount() != 2) return false;
            Inspector item = entry.field("item");
            Inspector weight = entry.field("weight");
            if (!item.valid() || !weight.valid()) return false;
            if (weight.type() != Type.LONG) return false;
            if (item.type() != Type.STRING && !settings.jsonWsetsAll) return false;
        }
        return true;
    }

    /** Unwrap to the underlying non-delegate sink to avoid nested converter state collisions. */
    private DataSink baseSink(DataSink sink) {
        while (sink instanceof DataSinkDelegate delegateSink) {
            sink = delegateSink.delegate;
        }
        return sink;
    }

    private String toFieldName(Inspector value) {
        return switch (value.type()) {
            case STRING -> value.asString();
            case LONG -> Long.toString(value.asLong());
            case DOUBLE -> Double.toString(value.asDouble());
            case BOOL -> value.asBool() ? "true" : "false";
            case DATA -> bytesToHex(value.asData());
            default -> throw new IllegalArgumentException("Cannot use " + value.type() + " as field name");
        };
    }

    private static String bytesToHex(byte[] data) {
        StringBuilder sb = new StringBuilder(2 + data.length * 2);
        sb.append("0x");
        for (byte b : data) {
            int v = b & 0xFF;
            sb.append("0123456789ABCDEF".charAt(v >>> 4));
            sb.append("0123456789ABCDEF".charAt(v & 0x0F));
        }
        return sb.toString();
    }
}
