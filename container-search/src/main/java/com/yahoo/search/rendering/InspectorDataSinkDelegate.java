package com.yahoo.search.rendering;

import com.yahoo.data.access.Inspector;
import com.yahoo.data.access.Type;
import com.yahoo.data.disclosure.DataSink;

class InspectorDataSinkDelegate extends DataSinkDelegate {

    private Inspector inspector;
    boolean isConverting = false;

    public InspectorDataSinkDelegate(DataSink delegate, Inspector inspector) {
        super(delegate);
        this.inspector = inspector;
    }

    @Override
    public void startArray() {
        if (isConvertibleToMap()) {
            pushDelegate(new MapDataSinkDelegate(delegate));
            isConverting = true;
        } else if (isConvertibleToWset()) {
            pushDelegate(new WsetDataSinkDelegate(delegate));
            isConverting = true;
        }
        delegate.startArray();
    }

    @Override
    public void endArray() {
        delegate.endArray();
        if (isConverting) {
            popDelegate();
            isConverting = false;
        }
    }

    boolean isConvertibleToMap() {
        if (inspector.type() != Type.ARRAY) {
            return false;
        }
        for (var entry : inspector.entries()) {
            if (entry.type() != Type.OBJECT || entry.fieldCount() != 2) return false;
            Inspector key = entry.field("key");
            Inspector value = entry.field("value");
            if (!key.valid() || !value.valid()) return false;
            // if (key.type() != Type.STRING && !settings.jsonMapsAll) return false;
        }
        return true;
    }

    boolean isConvertibleToWset() {
        if (inspector.type() != Type.ARRAY) {
            return false;
        }
        for (var entry : inspector.entries()) {
            if (entry.type() != Type.OBJECT || entry.fieldCount() != 2) return false;
            Inspector item = entry.field("item");
            Inspector weight = entry.field("weight");
            if (!item.valid() || !weight.valid()) return false;
            if (weight.type() != Type.LONG) return false;
            // if (item.type() != Type.STRING && !settings.jsonWsetsAll) return false;
        }
        return true;
    }
}
