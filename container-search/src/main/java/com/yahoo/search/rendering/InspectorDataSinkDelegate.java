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
                emitConvertedArray(inspector, new MapDataSinkDelegate(sink));
                return;
            }
            if (isConvertibleToWset(inspector, topLevel)) {
                emitConvertedArray(inspector, new WsetDataSinkDelegate(sink));
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

    private void emitConvertedArray(Inspector inspector, DataSink converter) {
        converter.startArray();
        for (var entry : inspector.entries()) {
            emitInspector(entry, converter, settings.convertDeep());
        }
        converter.endArray();
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
}
