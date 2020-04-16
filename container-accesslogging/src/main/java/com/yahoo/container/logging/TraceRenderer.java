package com.yahoo.container.logging;

import com.yahoo.data.access.Inspectable;
import com.yahoo.data.access.Inspector;
import com.yahoo.data.access.simple.JsonRender;
import com.yahoo.yolean.trace.TraceNode;
import com.yahoo.yolean.trace.TraceVisitor;
import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;

public class TraceRenderer extends TraceVisitor {
    private static final String TRACE_CHILDREN = "children";
    private static final String TRACE_MESSAGE = "message";
    private static final String TRACE_TIMESTAMP = "timestamp";
    private static final String TRACE = "trace";

    private final long basetime;
    private final JsonGenerator generator;
    private final FieldConsumer fieldConsumer;
    private boolean hasFieldName = false;
    int emittedChildNesting = 0;
    int currentChildNesting = 0;
    private boolean insideOpenObject = false;

    public interface FieldConsumer {
        void accept(Object object) throws IOException;
    }

    private static class Consumer implements FieldConsumer {
        private final JsonGenerator generator;

        Consumer(JsonGenerator generator) {
            this.generator = generator;
        }

        @Override
        public void accept(Object object) throws IOException {
            if (object instanceof Inspectable) {
                renderInspectorDirect(((Inspectable) object).inspect());
            } else {
                generator.writeObject(object);
            }
        }
        private void renderInspectorDirect(Inspector data) throws IOException {
            StringBuilder intermediate = new StringBuilder();
            JsonRender.render(data, intermediate, true);
            generator.writeRawValue(intermediate.toString());
        }
    }

    TraceRenderer(JsonGenerator generator, long basetime) {
        this(generator, new Consumer(generator), basetime);
    }
    public TraceRenderer(JsonGenerator generator, FieldConsumer consumer, long basetime) {
        this.generator = generator;
        this.fieldConsumer = consumer;
        this.basetime = basetime;
    }

    @Override
    public void entering(TraceNode node) {
        ++currentChildNesting;
    }

    @Override
    public void leaving(TraceNode node) {
        conditionalEndObject();
        if (currentChildNesting == emittedChildNesting) {
            try {
                generator.writeEndArray();
                generator.writeEndObject();
            } catch (IOException e) {
                throw new TraceRenderWrapper(e);
            }
            --emittedChildNesting;
        }
        --currentChildNesting;
    }

    @Override
    public void visit(TraceNode node) {
        try {
            doVisit(node.timestamp(), node.payload(), node.children().iterator().hasNext());
        } catch (IOException e) {
            throw new TraceRenderWrapper(e);
        }
    }

    private void doVisit(long timestamp, Object payload, boolean hasChildren) throws IOException {
        boolean dirty = false;
        if (timestamp != 0L) {
            header();
            generator.writeStartObject();
            generator.writeNumberField(TRACE_TIMESTAMP, timestamp - basetime);
            dirty = true;
        }
        if (payload != null) {
            if (!dirty) {
                header();
                generator.writeStartObject();
            }
            generator.writeFieldName(TRACE_MESSAGE);
            fieldConsumer.accept(payload);
            dirty = true;
        }
        if (dirty) {
            if (!hasChildren) {
                generator.writeEndObject();
            } else {
                setInsideOpenObject(true);
            }
        }
    }
    private void header() {
        fieldName();
        for (int i = 0; i < (currentChildNesting - emittedChildNesting); ++i) {
            startChildArray();
        }
        emittedChildNesting = currentChildNesting;
    }

    private void startChildArray() {
        try {
            conditionalStartObject();
            generator.writeArrayFieldStart(TRACE_CHILDREN);
        } catch (IOException e) {
            throw new TraceRenderWrapper(e);
        }
    }

    private void conditionalStartObject() throws IOException {
        if (!isInsideOpenObject()) {
            generator.writeStartObject();
        } else {
            setInsideOpenObject(false);
        }
    }

    private void conditionalEndObject() {
        if (isInsideOpenObject()) {
            // This triggers if we were inside a data node with payload and
            // subnodes, but none of the subnodes contained data
            try {
                generator.writeEndObject();
                setInsideOpenObject(false);
            } catch (IOException e) {
                throw new TraceRenderWrapper(e);
            }
        }
    }

    private void fieldName() {
        if (hasFieldName) {
            return;
        }

        try {
            generator.writeFieldName(TRACE);
        } catch (IOException e) {
            throw new TraceRenderWrapper(e);
        }
        hasFieldName = true;
    }

    boolean isInsideOpenObject() {
        return insideOpenObject;
    }

    void setInsideOpenObject(boolean insideOpenObject) {
        this.insideOpenObject = insideOpenObject;
    }
    public static final class TraceRenderWrapper extends RuntimeException {

        /**
         * Should never be serialized, but this is still needed.
         */
        private static final long serialVersionUID = 2L;

        TraceRenderWrapper(IOException wrapped) {
            super(wrapped);
        }

    }
}
