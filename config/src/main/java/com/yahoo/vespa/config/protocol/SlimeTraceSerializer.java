// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.protocol;

import com.yahoo.slime.Cursor;
import com.yahoo.yolean.trace.TraceNode;
import com.yahoo.yolean.trace.TraceVisitor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * Serialize a {@link TraceNode} to {@link com.yahoo.slime.Slime}.
 *
 * @author Ulf Lilleengen
 */
public class SlimeTraceSerializer extends TraceVisitor {
    static final String TIMESTAMP = "timestamp";
    static final String PAYLOAD = "payload";
    static final String CHILDREN = "children";
    final Deque<Cursor> cursors = new ArrayDeque<>();

    public SlimeTraceSerializer(Cursor cursor) {
        cursors.push(cursor);
    }

    @Override
    public void visit(TraceNode node) {
        Cursor current = cursors.pop();
        current.setLong(TIMESTAMP, node.timestamp());
        encodePayload(current, node.payload());
        addChildrenCursors(current, node);
    }

    private void encodePayload(Cursor current, Object payload) {
        if (payload instanceof String) {
            current.setString(PAYLOAD, (String)payload);
        } else if (payload instanceof Long) {
            current.setLong(PAYLOAD, (Long) payload);
        } else if (payload instanceof Boolean) {
            current.setBool(PAYLOAD, (Boolean) payload);
        } else if (payload instanceof Double) {
            current.setDouble(PAYLOAD, (Double) payload);
        } else if (payload instanceof byte[]) {
            current.setData(PAYLOAD, (byte[]) payload);
        }
    }

    private void addChildrenCursors(Cursor current, TraceNode node) {
        Iterator<TraceNode> it = node.children().iterator();
        if (it.hasNext()) {
            Cursor childrenArray = current.setArray(CHILDREN);
            while (it.hasNext()) {
                cursors.push(childrenArray.addObject());
                it.next();
            }
        }
    }
}
