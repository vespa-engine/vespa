// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.protocol;

import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Inspector;
import com.yahoo.yolean.trace.TraceNode;

/**
 * Deserializing from a {@link Inspector} (slime) representation to a {@link TraceNode}
 *
 * @author Ulf Lilleengen
 */
public class SlimeTraceDeserializer {
    private final Inspector entry;
    public SlimeTraceDeserializer(Inspector inspector) {
        this.entry = inspector;
    }

    public TraceNode deserialize() {
        return deserialize(entry);
    }

    private static TraceNode deserialize(Inspector entry) {
        Object payload = decodePayload(entry.field(SlimeTraceSerializer.PAYLOAD));
        long timestamp = decodeTimestamp(entry.field(SlimeTraceSerializer.TIMESTAMP));
        final TraceNode node = new TraceNode(payload, timestamp);
        Inspector children = entry.field(SlimeTraceSerializer.CHILDREN);
        children.traverse(new ArrayTraverser() {
            @Override
            public void entry(int idx, Inspector inspector) {
                node.add(deserialize(inspector));
            }
        });
        return node;
    }

    private static long decodeTimestamp(Inspector entry) {
        return entry.asLong();
    }

    private static Object decodePayload(Inspector entry) {
        switch (entry.type()) {
            case STRING:
                return entry.asString();
            case LONG:
                return entry.asLong();
            case BOOL:
                return entry.asBool();
            case DOUBLE:
                return entry.asDouble();
            case DATA:
                return entry.asData();
            default:
                return null;
        }
    }
}
