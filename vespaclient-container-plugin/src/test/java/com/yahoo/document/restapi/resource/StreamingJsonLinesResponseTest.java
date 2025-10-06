// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.restapi.resource;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.messagebus.Trace;
import com.yahoo.messagebus.TraceNode;
import com.yahoo.schema.derived.Deriver;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.serialization.JsonFormat;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * @author vekterli
 */
public class StreamingJsonLinesResponseTest {

    // Simple indirection to make matching write() calls easier by auto-converting
    // ByteBuffer content to UTF-8 strings.
    interface StringifiedResponseWriter {
        void commit(int status, String contentType, boolean fullyApplied) throws IOException;
        void write(String buffer, CompletionHandler completionHandlerOrNull);
        void close() throws IOException; // Narrowed exception specifier
    }

    static class StringifyingResponseWriterBridge implements ResponseWriter {
        private final StringifiedResponseWriter target;

        public StringifyingResponseWriterBridge(StringifiedResponseWriter target) {
            this.target = target;
        }

        @Override
        public void commit(int status, String contentType, boolean fullyApplied) throws IOException {
            target.commit(status, contentType, fullyApplied);
        }

        @Override
        public void write(ByteBuffer buffer, CompletionHandler completionHandlerOrNull) {
            target.write(new String(buffer.array(), StandardCharsets.UTF_8), completionHandlerOrNull);
        }

        @Override
        public void close() throws IOException {
            target.close();
        }
    }

    static class Fixture {
        final StringifiedResponseWriter writer;
        final StreamingJsonLinesResponse jsonlResponse;

        final DocumentmanagerConfig docConfig = Deriver
                .getDocumentManagerConfig("src/test/cfg/music.sd")
                .ignoreundefinedfields(true).build();
        final DocumentTypeManager manager = new DocumentTypeManager(docConfig);
        final Document doc1 = new Document(manager.getDocumentType("music"), "id:ns:music::one");
        final Document doc2 = new Document(manager.getDocumentType("music"), "id:ns:music::two");

        Fixture(JsonFormat.EncodeOptions tensorOptions) {
            writer = mock(StringifiedResponseWriter.class);
            var bridge = new StringifyingResponseWriterBridge(writer);
            jsonlResponse = new StreamingJsonLinesResponse(bridge, tensorOptions);
        }

        Fixture() {
            this(new JsonFormat.EncodeOptions());
        }
    }

    private static VisitorContinuation continuationOf(String token, double percentFinished) {
        return new VisitorContinuation(token, percentFinished);
    }

    private static VisitorContinuation continuationOf(String token) {
        return continuationOf(token, 50.0);
    }

    @Test
    void commit_is_forwarded_to_writer_with_jsonl_content_type() throws IOException {
        var f = new Fixture();
        f.jsonlResponse.commit(200, true);
        verify(f.writer).commit(200, "application/jsonl; charset=UTF-8", true);
    }

    @Test
    void close_is_forwarded_to_writer() throws IOException {
        var f = new Fixture();
        f.jsonlResponse.close();
        verify(f.writer).close();
    }

    @Test
    void non_jsonl_supported_features_do_not_result_in_writes() throws IOException {
        var f = new Fixture();
        f.jsonlResponse.writeDocumentsArrayStart();
        f.jsonlResponse.writeDocumentsArrayEnd();
        verify(f.writer, never()).write(anyString(), any());
    }

    @Test
    void operations_are_written_as_json_lines_in_order() throws IOException {
        var f = new Fixture();
        f.doc1.setFieldValue("artist", "Boyzvoice");
        f.jsonlResponse.writeDocumentValue(f.doc1, null);
        f.jsonlResponse.writeDocumentValue(f.doc2, null);
        f.jsonlResponse.writeDocumentRemoval(new DocumentId("id:ns:music::three"), null);

        var inOrder = Mockito.inOrder(f.writer);
        inOrder.verify(f.writer).write("{\"put\":\"id:ns:music::one\",\"fields\":{\"artist\":\"Boyzvoice\"}}\n", null);
        inOrder.verify(f.writer).write("{\"put\":\"id:ns:music::two\",\"fields\":{}}\n", null);
        inOrder.verify(f.writer).write("{\"remove\":\"id:ns:music::three\"}\n", null);
    }

    @Test
    void inline_continuation_tokens_are_not_written_immediately() throws IOException {
        // See deadlock danger comment in the implementation for a rationale on why
        // we can't write to the underlying channel as part of the token update itself.
        var f = new Fixture();
        f.jsonlResponse.reportUpdatedContinuation(() -> continuationOf("cooltoken2000"));
        verify(f.writer, never()).write(anyString(), any());
    }

    @Test
    void inline_continuation_token_updates_are_written_as_part_of_next_put() throws IOException {
        var f = new Fixture();
        f.jsonlResponse.reportUpdatedContinuation(() -> continuationOf("cooltoken2000"));
        // Token update and put is coalesced into the same buffer write.
        f.jsonlResponse.writeDocumentValue(f.doc1, null);
        String expected = """
                {"put":"id:ns:music::one","fields":{}}
                {"continuation":{"token":"cooltoken2000","percentFinished":50.0}}
                """;
        verify(f.writer).write(expected, null);
        // Writing another put should _not_ have the token
        f.jsonlResponse.writeDocumentValue(f.doc2, null);
        expected = """
                {"put":"id:ns:music::two","fields":{}}
                """;
        verify(f.writer).write(expected, null);
    }

    @Test
    void inline_continuation_token_updates_are_written_as_part_of_next_remove() throws IOException {
        var f = new Fixture();
        f.jsonlResponse.reportUpdatedContinuation(() -> continuationOf("cooltoken3000", 30.0));
        f.jsonlResponse.writeDocumentRemoval(new DocumentId("id:ns:music::three"), null);
        String expected = """
                {"remove":"id:ns:music::three"}
                {"continuation":{"token":"cooltoken3000","percentFinished":30.0}}
                """;
        verify(f.writer).write(expected, null);
        f.jsonlResponse.writeDocumentRemoval(new DocumentId("id:ns:music::four"), null);
        expected = """
                {"remove":"id:ns:music::four"}
                """;
        verify(f.writer).write(expected, null);
    }

    @Test
    void multiple_continuation_token_updates_retain_newest_token_value() throws IOException {
        var f = new Fixture();
        f.jsonlResponse.reportUpdatedContinuation(() -> continuationOf("cooltoken2000", 20.0));
        f.jsonlResponse.reportUpdatedContinuation(() -> continuationOf("cooltoken3000", 30.0));
        f.jsonlResponse.reportUpdatedContinuation(() -> continuationOf("cooltoken4000", 40.0));
        f.jsonlResponse.writeDocumentValue(f.doc1, null);
        String expected = """
                {"put":"id:ns:music::one","fields":{}}
                {"continuation":{"token":"cooltoken4000","percentFinished":40.0}}
                """;
        verify(f.writer).write(expected, null);
    }

    @Test
    void epilogue_continuation_token_is_written_immediately() throws IOException {
        var f = new Fixture();
        // Not written while session lock is held, so it's safe to write it immediately.
        f.jsonlResponse.writeEpilogueContinuation(continuationOf("swagtoken"));
        String expected = """
                {"continuation":{"token":"swagtoken","percentFinished":50.0}}
                """;
        verify(f.writer).write(expected, null);
    }

    @Test
    void finished_epilogue_continuation_emits_continuation_with_no_token_and_100_pct_completion() throws IOException {
        var f = new Fixture();
        f.jsonlResponse.writeEpilogueContinuation(VisitorContinuation.FINISHED);
        String expected = """
                {"continuation":{"percentFinished":100.0}}
                """;
        verify(f.writer).write(expected, null);
    }

    private static void verifyCompletionHandlerUntouched(CompletionHandler handler) {
        // We should not invoke the handler ourselves, only forward it
        verify(handler, never()).completed();
        verify(handler, never()).failed(any());
    }

    @Test
    void put_completion_handler_is_forwarded_to_writer() throws IOException {
        var f = new Fixture();
        var myHandler = mock(CompletionHandler.class);
        f.jsonlResponse.writeDocumentValue(f.doc1, myHandler);
        verify(f.writer).write(anyString(), same(myHandler));
        verifyCompletionHandlerUntouched(myHandler);
    }

    @Test
    void remove_completion_handler_is_forwarded_to_writer() throws IOException {
        var f = new Fixture();
        var myHandler = mock(CompletionHandler.class);
        f.jsonlResponse.writeDocumentRemoval(new DocumentId("id:ns:music::zoid"), myHandler);
        verify(f.writer).write(anyString(), same(myHandler));
        verifyCompletionHandlerUntouched(myHandler);
    }

    @Test
    void provided_tensor_options_are_used_when_rendering_put() throws IOException {
        var f = new Fixture(new JsonFormat.EncodeOptions(true)); // short form
        f.doc1.setFieldValue("embedding", new TensorFieldValue(Tensor.from("tensor(x[3]):[1,2,3]")));
        f.jsonlResponse.writeDocumentValue(f.doc1, null);
        String expected = """
                {"put":"id:ns:music::one","fields":{"embedding":{"type":"tensor(x[3])","values":[1.0,2.0,3.0]}}}
                """;
        verify(f.writer).write(expected, null);

        f = new Fixture(new JsonFormat.EncodeOptions(true, false, true)); // hex form
        f.doc1.setFieldValue("embedding", new TensorFieldValue(Tensor.from("tensor(x[3]):[4,5,6]")));
        f.jsonlResponse.writeDocumentValue(f.doc1, null);
        expected = """
                {"put":"id:ns:music::one","fields":{"embedding":{"type":"tensor(x[3])","values":"401000000000000040140000000000004018000000000000"}}}
                """;
        verify(f.writer).write(expected, null);
    }

    @Test
    void trace_is_rendered_as_own_line() throws IOException {
        var f = new Fixture();
        var trace = new Trace(9);
        trace.trace(7, "Poirot's deductions", false);
        trace.getRoot().addChild(new TraceNode().setStrict(false)
                .addChild("The butler did it")
                .addChild("Weapon was a garden gnome"));
        f.jsonlResponse.writeTrace(trace);
        // TODO the nested "trace" objects aren't beautiful; reconsider format for JSONL.
        //  Since trace nodes may technically output "message" objects at any level, need a wrapper.
        String expected = """
                {"trace":{"trace":[{"message":"Poirot's deductions"},{"fork":[{"message":"The butler did it"},{"message":"Weapon was a garden gnome"}]}]}}
                """;
        verify(f.writer).write(expected, null);
    }

    @Test
    void message_is_rendered_as_own_line() throws IOException {
        var f = new Fixture();
        f.jsonlResponse.writeMessage("'ello, 'ello, this is London", StreamableJsonResponse.MessageSeverity.INFO);
        f.jsonlResponse.writeMessage("seagulls incoming", StreamableJsonResponse.MessageSeverity.WARNING);
        f.jsonlResponse.writeMessage("seagulls have arrived, flee for your lives", StreamableJsonResponse.MessageSeverity.ERROR);

        String expected = """
                {"message":{"text":"'ello, 'ello, this is London","severity":"info"}}
                """;
        verify(f.writer).write(expected, null);
        expected = """
                {"message":{"text":"seagulls incoming","severity":"warning"}}
                """;
        verify(f.writer).write(expected, null);
        expected = """
                {"message":{"text":"seagulls have arrived, flee for your lives","severity":"error"}}
                """;
        verify(f.writer).write(expected, null);
    }

    @Test
    void document_count_is_rendered_as_own_line() throws IOException {
        var f = new Fixture();
        f.jsonlResponse.writeDocumentCount(123456);
        String expected = """
                {"documentCount":123456}
                """;
        verify(f.writer).write(expected, null);
    }

}
