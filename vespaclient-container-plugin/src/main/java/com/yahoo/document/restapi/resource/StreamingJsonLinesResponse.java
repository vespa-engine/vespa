// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.restapi.resource;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.json.JsonWriter;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.messagebus.Trace;
import com.yahoo.tensor.serialization.JsonFormat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Supplier;

/**
 * <p>A streaming JSONL response writer where puts and removes are written in the
 * Vespa JSONL feed format. Since the output also contains other non-put/remove
 * entries, this is a strict <em>superset</em> of the feed format, and clients must
 * take that into account.</p>
 *
 * <p>One major difference from the "restful" JSON responses (streaming or not) is
 * that continuation tokens are emitted inline as they are updated by the visitor
 * session, rather than being emitted once as part of the response epilogue. This
 * means a client can continuously observe updated token values and simply remember
 * the last one they have observed. Visiting can then restart from that point with
 * minimal loss of progress.</p>
 *
 * @author vekterli
 */
class StreamingJsonLinesResponse implements StreamableJsonResponse {

    private static final JsonFactory JSON_FACTORY = new JsonFactoryBuilder()
            .streamReadConstraints(StreamReadConstraints.builder()
                    .maxStringLength(Integer.MAX_VALUE).build())
                    // Do not insert a space between root-level objects
                    .rootValueSeparator((String)null)
            .build();

    private static final int DEFAULT_STREAM_ALLOC_SIZE = 32; // TODO a reasonable default

    private final ResponseWriter responseWriter;
    private final JsonFormat.EncodeOptions tensorOptions;
    private final Object lock = new Object();
    private VisitorContinuation pendingContinuation = null;

    public StreamingJsonLinesResponse(ResponseWriter responseWriter, JsonFormat.EncodeOptions tensorOptions) {
        this.responseWriter = responseWriter;
        this.tensorOptions = tensorOptions;
    }

    @Override
    public void commit(int status, boolean fullyApplied) throws IOException {
        // TODO decide proper content type
        responseWriter.commit(status, "application/jsonl; charset=UTF-8", fullyApplied);
    }

    @Override
    public void writeDocumentsArrayStart() throws IOException {
        // Ignored; not part of JSONL response format
    }

    @Override
    public void writeDocumentsArrayEnd() throws IOException {
        // Ignored; not part of JSONL response format
    }

    @FunctionalInterface
    interface MyJsonWriter {
        void writeInto(JsonGenerator json) throws IOException;
    }

    private void writeJsonLine(MyJsonWriter jsonWriter, CompletionHandler completionHandler) throws IOException {
        ByteArrayOutputStream myOut = new ByteArrayOutputStream(DEFAULT_STREAM_ALLOC_SIZE);
        try (JsonGenerator json = JSON_FACTORY.createGenerator(myOut)) {
            jsonWriter.writeInto(json);
            json.writeRaw('\n');
        }
        responseWriter.write(ByteBuffer.wrap(myOut.toByteArray()), completionHandler);
    }

    // Write JSONL line with no associated explicit completion handler
    private void writeJsonLine(MyJsonWriter jsonWriter) throws IOException {
        writeJsonLine(jsonWriter, null);
    }

    @Override
    public void writeDocumentValue(Document document, CompletionHandler completionHandler) throws IOException {
        writeJsonLine((json) -> {
            json.writeStartObject();
            json.writeFieldName(JsonNames.PUT);
            json.writeString(document.getId().toString());
            new JsonWriter(json, tensorOptions).writeFields(document);
            json.writeEndObject();
            appendUpdatedContinuationLineIfPresent(json);
        }, completionHandler);
    }

    @Override
    public void writeDocumentRemoval(DocumentId id, CompletionHandler completionHandler) throws IOException {
        writeJsonLine((json) -> {
            json.writeStartObject();
            json.writeFieldName(JsonNames.REMOVE);
            json.writeString(id.toString());
            json.writeEndObject();
            appendUpdatedContinuationLineIfPresent(json);
        }, completionHandler);
    }

    @Override
    public void reportUpdatedContinuation(Supplier<VisitorContinuation> continuationSupplier) throws IOException {
        // Continuation token updates happen within the context of the main visitor session lock,
        // so we cannot directly write the token here, or we risk forming a following locking cycle
        // with the jDisc response queue lock:
        //
        //  thread 1: mbus doc callback -> write doc -> jDisc queue (lock A) -> completion handler -> session ack (lock B)
        //  thread 2: mbus session (lock B) -> progress callback -> write -> jDisc queue (lock A)
        //
        // et voilà; deadlock heaven.
        //
        // Instead, buffer it up to write it with the next put/remove. If there are no more
        // put/remove ops, we'll get the token in the epilogue write anyway. As a bonus, by doing
        // it this way we avoid duplicate token updates upon session close, as that will otherwise
        // first give a token update, then write the epilogue token (iff the session has not
        // already completed).
        VisitorContinuation continuation = continuationSupplier.get();
        synchronized (lock) {
            // It's OK to overwrite any existing pending continuation token since updates are
            // strictly ordered and later updates shall subsume previous updates.
            pendingContinuation = continuation;
        }
    }

    /**
     * Iff there is a pending continuation token, append as own JSONL line.
     * <em>At least</em> one line must already have been written to <code>json</code>.
     * Caller must ensure the appended line gets terminated with a newline character;
     * this function does not do this.
     */
    private void appendUpdatedContinuationLineIfPresent(JsonGenerator json) throws IOException {
        // _must not_ write anything to the wire that can take the big jDisc response lock!
        synchronized (lock) {
            if (pendingContinuation != null) {
                json.writeRaw('\n'); // End of previous JSON line
                appendContinuationToken(json, pendingContinuation);
                pendingContinuation = null;
            }
        }
    }

    private void appendContinuationToken(JsonGenerator json, VisitorContinuation continuation) throws IOException {
        json.writeStartObject();
        json.writeFieldName(JsonNames.CONTINUATION);
        json.writeStartObject();
        if (continuation.hasRemaining()) {
            json.writeFieldName(JsonNames.TOKEN);
            json.writeString(continuation.token());
        }
        json.writeFieldName(JsonNames.PERCENT_FINISHED);
        json.writeNumber(continuation.percentFinished());
        json.writeEndObject();
        json.writeEndObject();
    }

    @Override
    public void writeEpilogueContinuation(VisitorContinuation continuation) throws IOException {
        writeJsonLine((json) -> {
            appendContinuationToken(json, continuation);
        });
    }

    @Override
    public void writeTrace(Trace trace) throws IOException {
        writeJsonLine((json) -> {
            json.writeStartObject();
            json.writeFieldName(JsonNames.TRACE);
            json.writeStartObject();
            TraceJsonRenderer.writeTrace(json, trace.getRoot());
            json.writeEndObject();
            json.writeEndObject();
        });
    }

    @Override
    public void writeMessage(String message, MessageSeverity severity) throws IOException {
        writeJsonLine((json) -> {
            json.writeStartObject();
            json.writeFieldName(JsonNames.MESSAGE);
            json.writeStartObject();
            json.writeFieldName(JsonNames.TEXT);
            json.writeString(message);
            json.writeFieldName(JsonNames.SEVERITY);
            json.writeString(severity.jsonValue());
            json.writeEndObject();
            json.writeEndObject();
        });
    }

    @Override
    public void writeDocumentCount(long count) throws IOException {
        // TODO do we want this for streaming or do we want another kind of session summary?
        writeJsonLine((json) -> {
            json.writeStartObject();
            json.writeFieldName(JsonNames.DOCUMENT_COUNT);
            json.writeNumber(count);
            json.writeEndObject();
        });
    }

    @Override
    public void close() throws IOException {
        responseWriter.close();
    }
}
