// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.rendering;

import com.yahoo.processing.Request;
import com.yahoo.processing.Response;
import com.yahoo.processing.handler.ResponseHeaders;
import com.yahoo.processing.handler.ResponseStatus;
import com.yahoo.processing.request.ErrorMessage;
import com.yahoo.processing.response.Data;
import com.yahoo.processing.response.DataList;
import com.yahoo.text.JSONWriter;
import com.yahoo.yolean.trace.TraceNode;
import com.yahoo.yolean.trace.TraceVisitor;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * The default renderer for processing responses. Renders a response in JSON.
 * This can be overridden to specify particular rendering of leaf Data elements.
 * This default implementation renders the toString of each element.
 *
 * @author bratseth
 */
public class ProcessingRenderer extends AsynchronousSectionedRenderer<Response> {

    private Map<Request,Request> renderedRequests = new IdentityHashMap<>();

    private JSONWriter jsonWriter;

    /** The current nesting level */
    private int level;

    @Override
    public void init() {
        super.init();
        level = 0;
    }

    @Override
    public final void beginResponse(OutputStream stream) throws IOException {
        jsonWriter = new JSONWriter(stream);
    }

    @Override
    public final void endResponse() throws IOException {
    }

    @Override
    public final void beginList(DataList<?> list) throws IOException {
        if (level>0)
            jsonWriter.beginArrayValue();

        jsonWriter.beginObject();

        if (level==0)
            renderTrace();

        if ( ! list.request().errors().isEmpty() && ! rendered(list.request())) {
            jsonWriter.beginField("errors");
            jsonWriter.beginArray();
            for (ErrorMessage error : list.request().errors()) {
                if (renderedRequests == null)
                    renderedRequests = new IdentityHashMap<>();
                renderedRequests.put(list.request(),list.request());
                jsonWriter.beginArrayValue();
                if (error.getCause() != null) { // render object
                    jsonWriter.beginObject();
                    jsonWriter.beginField("error").value(error.toString()).endField();
                    jsonWriter.beginField("stacktrace").value(stackTraceAsString(error.getCause())).endField();
                    jsonWriter.endObject();
                }
                else { // render string
                    jsonWriter.value(error.toString());
                }
                jsonWriter.endArrayValue();
            }
            jsonWriter.endArray();
            jsonWriter.endField();
        }

        jsonWriter.beginField("datalist");
        jsonWriter.beginArray();
        level++;
    }

    private String stackTraceAsString(Throwable e) {
        StringWriter writer = new StringWriter();
        e.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private boolean rendered(Request request) {
        return renderedRequests != null && renderedRequests.containsKey(request);
    }

    @Override
    public final void endList(DataList<?> list) throws IOException {
        jsonWriter.endArray();
        jsonWriter.endField();
        jsonWriter.endObject();
        if (level>0)
            jsonWriter.endArrayValue();
        level--;
    }

    @Override
    public final void data(Data data) throws IOException {
        if (! shouldRender(data)) return;
        jsonWriter.beginArrayValue();
        jsonWriter.beginObject();
        jsonWriter.beginField("data");
        renderValue(data,jsonWriter);
        jsonWriter.endField();
        jsonWriter.endObject();
        jsonWriter.endArrayValue();
    }

    /**
     * Renders the value of a data element.
     * This default implementation does writer.fieldValue(data.toString())
     * Override this to render data in application specific ways.
     */
    protected void renderValue(Data data,JSONWriter writer) throws IOException {
        writer.value(data.toString());
    }

    /**
     * Returns whether this data element should be rendered.
     * This can be overridden to add new kinds of data which should not be rendered.
     * This default implementation returns true unless the data is instanceof ResponseHeaders.
     *
     * @return true to render it, false to skip completely
     */
    protected boolean shouldRender(Data data) {
        if (data instanceof ResponseHeaders) return false;
        if (data instanceof ResponseStatus) return false;
        return true;
    }

    @Override
    public final String getEncoding() {
        return null;
    }

    @Override
    public final String getMimeType() {
        return "application/json";
    }

    private boolean renderTrace() throws IOException {
        if (getExecution().trace().getTraceLevel() == 0) return false;

        jsonWriter.beginField("trace");
        try {
            getExecution().trace().traceNode().accept(new TraceRenderingVisitor(jsonWriter));
        } catch (WrappedIOException e) {
            throw e.getCause();
        }
        jsonWriter.endField();
        return true;
    }

    private static class TraceRenderingVisitor extends TraceVisitor {

        private final JSONWriter jsonWriter;

        public TraceRenderingVisitor(JSONWriter jsonWriter) {
            this.jsonWriter = jsonWriter;
        }

        @Override
        public void entering(TraceNode node) {
            try {
                jsonWriter.beginArray();
            }
            catch (IOException e) {
                throw new WrappedIOException(e);
            }
        }

        @Override
        public void leaving(TraceNode node) {
            try {
                jsonWriter.endArray();
            }
            catch (IOException e) {
                throw new WrappedIOException(e);
            }
        }

        @Override
        public void visit(TraceNode node) {
            if ( ! (node.payload() instanceof String)) return; // skip other info than trace messages
            try {
                jsonWriter.beginArrayValue();
                if (node.timestamp() != 0) { // render object
                    jsonWriter.beginObject();
                    jsonWriter.beginField("timestamp").value(node.timestamp()).endField();
                    jsonWriter.beginField("message").value(node.payload().toString()).endField();
                    jsonWriter.endObject();
                }
                else { // render string
                    jsonWriter.value(node.payload().toString());
                }
                jsonWriter.endArrayValue();
            }
            catch (IOException e) {
                throw new WrappedIOException(e);
            }
        }

    }

    private static class WrappedIOException extends RuntimeException {
        private WrappedIOException(IOException cause) {
            super(cause);
        }

        @Override
        public IOException getCause() {
            return (IOException) super.getCause();
        }
    }

}
