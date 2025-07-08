// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.restapi.resource;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.yahoo.container.jdisc.ContentChannelOutputStream;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.json.JsonWriter;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.BufferedContentChannel;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.messagebus.Trace;
import com.yahoo.tensor.serialization.JsonFormat;
import com.yahoo.vespa.http.server.Headers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.logging.Logger;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.WARNING;

/**
 * Class for writing and returning JSON responses to document operations in a thread safe manner.
 *
 * @author Jon Marius Venstad
 */
class JsonResponse implements StreamableJsonResponse {

    private static final Logger log = Logger.getLogger(JsonResponse.class.getName());

    private static final ByteBuffer emptyBuffer = ByteBuffer.wrap(new byte[0]);
    private static final int FLUSH_SIZE = 128;

    private static final CompletionHandler logException = new CompletionHandler() {
        @Override public void completed() { }
        @Override public void failed(Throwable t) {
            log.log(FINE, "Exception writing or closing response data", t);
        }
    };

    private static final JsonFactory jsonFactory = new JsonFactoryBuilder()
            .streamReadConstraints(StreamReadConstraints.builder().maxStringLength(Integer.MAX_VALUE).build())
            .build();

    private final BufferedContentChannel buffer = new BufferedContentChannel();
    private final OutputStream out = new ContentChannelOutputStream(buffer);
    private final JsonGenerator json;
    private final ResponseHandler handler;
    private final Queue<CompletionHandler> acks = new ConcurrentLinkedQueue<>();
    private final Queue<ByteArrayOutputStream> docs = new ConcurrentLinkedQueue<>();
    private final AtomicLong documentsWritten = new AtomicLong();
    private final AtomicLong documentsFlushed = new AtomicLong();
    private final AtomicLong documentsAcked = new AtomicLong();
    private final JsonFormat.EncodeOptions tensorOptions;
    private boolean documentsDone = false;
    private boolean first = true;
    private ContentChannel channel;

    private JsonResponse(ResponseHandler handler, JsonFormat.EncodeOptions tensorOptions) throws IOException {
        this.handler = handler;
        this.tensorOptions = tensorOptions;
        json = jsonFactory.createGenerator(out);
        json.writeStartObject();
    }

    /**
     * Creates a new JsonResponse with path and id fields written.
     */
    static JsonResponse createWithPathAndId(DocumentPath path, ResponseHandler handler, JsonFormat.EncodeOptions tensorOptions) throws IOException {
        JsonResponse response = new JsonResponse(handler, tensorOptions);
        response.writePathId(path.rawPath());
        response.writeDocId(path.id());
        return response;
    }

    /**
     * Creates a new JsonResponse with path field written.
     */
    static JsonResponse createWithPath(HttpRequest request, ResponseHandler handler, JsonFormat.EncodeOptions tensorOptions) throws IOException {
        JsonResponse response = new JsonResponse(handler, tensorOptions);
        response.writePathId(request.getUri().getRawPath());
        return response;
    }

    /**
     * Creates a new JsonResponse with path and message fields written.
     */
    static JsonResponse createWithPathAndMessage(HttpRequest request, String message, ResponseHandler handler, JsonFormat.EncodeOptions tensorOptions) throws IOException {
        JsonResponse response = createWithPath(request, handler, tensorOptions);
        response.writeMessage(message);
        return response;
    }

    synchronized void commit(int status) throws IOException {
        commit(status, true);
    }

    /**
     * Commits a response with the given status code and some default headers, and writes whatever content is buffered.
     */
    @Override
    public synchronized void commit(int status, boolean fullyApplied) throws IOException {
        Response response = new Response(status);
        response.headers().add("Content-Type", List.of("application/json; charset=UTF-8"));
        if (!fullyApplied) {
            response.headers().add(Headers.IGNORED_FIELDS, "true");
        }
        try {
            channel = handler.handleResponse(response);
            buffer.connectTo(channel);
        } catch (RuntimeException e) {
            throw new IOException(e);
        }
    }

    /**
     * Commits a response with the given status code and some default headers, writes buffered content, and closes this.
     */
    synchronized void respond(int status) throws IOException {
        try (this) {
            commit(status);
        }
    }

    /**
     * Closes the JSON and the output content channel of this.
     */
    @Override
    public synchronized void close() throws IOException {
        documentsDone = true; // In case we were closed without explicitly closing the documents array.
        try {
            if (channel == null) {
                log.log(WARNING, "Close called before response was committed");
                commit(Response.Status.INTERNAL_SERVER_ERROR);
            }
            json.close(); // Also closes object and array scopes.
            out.close();  // Simply flushes the output stream.
        } finally {
            if (channel != null) {
                channel.close(logException); // Closes the response handler's content channel.
            }
        }
    }

    synchronized void writePathId(String path) throws IOException {
        json.writeFieldName(JsonNames.PATH_ID);
        json.writeString(path);
    }

    @Override
    public synchronized void writeMessage(String message) throws IOException {
        json.writeFieldName(JsonNames.MESSAGE);
        json.writeString(message);
    }

    @Override
    public synchronized void writeDocumentCount(long count) throws IOException {
        json.writeFieldName(JsonNames.DOCUMENT_COUNT);
        json.writeNumber(count);
    }

    synchronized void writeDocId(DocumentId id) throws IOException {
        json.writeFieldName(JsonNames.ID);
        json.writeString(id.toString());
    }

    @Override
    public synchronized void writeTrace(Trace trace) throws IOException {
        if (trace != null && !trace.getRoot().isEmpty()) {
            TraceJsonRenderer.writeTrace(json, trace.getRoot());
        }
    }

    private JsonFormat.EncodeOptions tensorOptions() {
        return this.tensorOptions;
    }

    private boolean tensorShortForm() {
        return tensorOptions().shortForm();
    }

    private boolean tensorDirectValues() {
        return tensorOptions().directValues();
    }

    synchronized void writeSingleDocument(Document document) throws IOException {
        new JsonWriter(json, tensorOptions()).writeFields(document);
    }

    @Override
    public synchronized void writeDocumentsArrayStart() throws IOException {
        json.writeFieldName(JsonNames.DOCUMENTS);
        json.writeStartArray();
    }

    private interface DocumentWriter {
        void write(ByteArrayOutputStream out) throws IOException;
    }

    /**
     * Writes documents to an internal queue, which is flushed regularly.
     */
    @Override
    public void writeDocumentValue(Document document, CompletionHandler completionHandler) throws IOException {
        writeDocument(myOut -> {
            try (JsonGenerator myJson = jsonFactory.createGenerator(myOut)) {
                // TODO shouldn't this just take tensorOptions directly?
                //  This does not actually allow for hex format rendering...!
                new JsonWriter(myJson, tensorShortForm(), tensorDirectValues()).write(document);
            }
        }, completionHandler);
    }

    @Override
    public void writeDocumentRemoval(DocumentId id, CompletionHandler completionHandler) throws IOException {
        writeDocument(myOut -> {
            try (JsonGenerator myJson = jsonFactory.createGenerator(myOut)) {
                myJson.writeStartObject();
                myJson.writeFieldName(JsonNames.REMOVE);
                myJson.writeString(id.toString());
                myJson.writeEndObject();
            }
        }, completionHandler);
    }

    /**
     * Writes documents to an internal queue, which is flushed regularly.
     */
    void writeDocument(DocumentWriter documentWriter, CompletionHandler completionHandler) throws IOException {
        // Serialise document and add to queue, not necessarily in the order dictated by "written" above,
        // i.e., the first 128 documents in the queue are not necessarily the ones ack'ed early.
        ByteArrayOutputStream myOut = new ByteArrayOutputStream(1);
        myOut.write(','); // Prepend rather than append, to avoid double memory copying.
        documentWriter.write(myOut);
        docs.add(myOut);

        // It is crucial that ACKing of in-flight operations happens _after_ the document payload is
        // visible in the `docs` queue. Otherwise, there is a risk that the ACK sets in motion a
        // full unwind of the entire visitor session from the content node back to the client session
        // (that's us), triggering the `onDone` callback and transitively the final flush of enqueued
        // documents. If `myOut` is then not part of `docs`, it won't be rendered at all.
        // This is a Dark Souls-tier distributed race condition.
        if (completionHandler != null) {
            acks.add(completionHandler);
            ackDocuments();
        }

        // Flush the first FLUSH_SIZE documents in the queue to the network layer if chunk is filled.
        if (documentsWritten.incrementAndGet() % FLUSH_SIZE == 0) {
            flushDocuments();
        }
    }

    void ackDocuments() {
        while (documentsAcked.incrementAndGet() <= documentsFlushed.get() + FLUSH_SIZE) {
            CompletionHandler ack = acks.poll();
            if (ack != null) {
                ack.completed();
            } else {
                break;
            }
        }
        documentsAcked.decrementAndGet(); // We overshoot by one above, so decrement again when done.
    }

    synchronized void flushDocuments() throws IOException {
        for (int i = 0; i < FLUSH_SIZE; i++) {
            ByteArrayOutputStream doc = docs.poll();
            if (doc == null) {
                break;
            }

            if (!documentsDone) {
                if (first) { // First chunk, remove leading comma from first document, and flush "json" to "buffer".
                    json.flush();
                    buffer.write(ByteBuffer.wrap(doc.toByteArray(), 1, doc.size() - 1), null);
                    first = false;
                } else {
                    buffer.write(ByteBuffer.wrap(doc.toByteArray()), null);
                }
            }
        }

        // Ensure new, eligible acks are done, after flushing these documents.
        buffer.write(emptyBuffer, new CompletionHandler() {
            @Override
            public void completed() {
                documentsFlushed.addAndGet(FLUSH_SIZE);
                ackDocuments();
            }

            @Override
            public void failed(Throwable t) {
                // This is typically caused by the client closing the connection during production of the response content.
                log.log(FINE, "Error writing documents", t);
                completed();
            }
        });
    }

    @Override
    public synchronized void writeDocumentsArrayEnd() throws IOException {
        flushDocuments();
        documentsDone = true;
        json.writeEndArray();
    }

    @Override
    public void reportUpdatedContinuation(Supplier<String> token) throws IOException {
        // Not supported by the restful Document V1 format; only a single epilogue
        // continuation token may be emitted.
    }

    @Override
    public synchronized void writeEpilogueContinuation(String token) throws IOException {
        json.writeFieldName(JsonNames.CONTINUATION);
        json.writeString(token);
    }

}
