// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.data.access;

import com.yahoo.api.annotations.Beta;
import com.yahoo.data.disclosure.DataSink;
import com.yahoo.data.disclosure.DataSource;

/**
 * Walks a tree of objects implementing {@link Inspector} and emits to a {@link DataSink}.
 *
 * @author johsol
 */
@Beta
public class InspectorDataSource implements DataSource {

    private final Inspector inspector;

    public InspectorDataSource(Inspector inspector) {
        this.inspector = inspector;
    }

    @Override
    public void emit(DataSink sink) {
        walk(sink, inspector);
    }

    private void walk(DataSink sink, Inspector inspector) {
        switch (inspector.type()) {
            case EMPTY -> {
                sink.emptyValue();
            }
            case BOOL -> {
                sink.booleanValue(inspector.asBool());
            }
            case LONG -> {
                sink.longValue(inspector.asLong());
            }
            case DOUBLE -> {
                sink.doubleValue(inspector.asDouble());
            }
            case STRING -> {
                sink.stringValue(inspector.asString(), inspector.asUtf8());
            }
            case DATA -> {
                sink.dataValue(inspector.asData());
            }
            case ARRAY -> {
                sink.startArray();
                for (int i = 0; i < inspector.entryCount(); i++) {
                    walk(sink, inspector.entry(i));
                }
                sink.endArray();
            }
            case OBJECT -> {
                sink.startObject();
                for (var field : inspector.fields()) {
                    sink.fieldName(field.getKey());
                    walk(sink, field.getValue());
                }
                sink.endObject();
            }
        }
    }
}
