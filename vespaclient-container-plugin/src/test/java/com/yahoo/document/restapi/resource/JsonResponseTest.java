// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.restapi.resource;

import com.yahoo.document.DocumentId;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.tensor.serialization.JsonFormat;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests how JsonResponse writes visited documents to the response channel.
 */
class JsonResponseTest {

    /** Records every write and completes it at once. */
    private static class RecordingChannel implements ContentChannel {
        final List<byte[]> writes = new ArrayList<>();
        @Override public void write(ByteBuffer buffer, CompletionHandler handler) {
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            writes.add(bytes);
            if (handler != null) handler.completed();
        }
        @Override public void close(CompletionHandler handler) { if (handler != null) handler.completed(); }
    }

    @Test
    void documents_are_written_together_in_writes_of_at_most_64_KiB() throws Exception {
        // 300 documents of about 2 KB, so 32 fit in 64 KiB. They are flushed in batches of 128, 128 and 44.
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 300; i++) ids.add(String.format(Locale.ROOT, "id:ns:music::%03d-", i) + "x".repeat(2_000));
        int size = removalJson(ids.get(0)).length();
        assertEquals(32, 64 * 1024 / size);

        // Empty writes release acknowledgements after the preceding documents.
        List<Integer> expected = List.of(size - 1, 32 * size, 32 * size, 32 * size, 31 * size, 0,
                                         32 * size, 32 * size, 32 * size, 32 * size, 0,
                                         32 * size, 12 * size, 0);
        assertRemovals(ids, expected);
    }

    @Test
    void a_document_larger_than_64_KiB_is_written_alone() throws Exception {
        String small = "id:ns:music::small-" + "x".repeat(2_000);
        String large = "id:ns:music::large-" + "x".repeat(65_511);
        assertEquals(64 * 1024 + 8, removalJson(large).length());
        List<String> ids = List.of(small, small, large, small);

        int smallSize = removalJson(small).length();
        List<Integer> expected = List.of(smallSize - 1, smallSize, removalJson(large).length(), smallSize, 0);
        assertRemovals(ids, expected);
    }

    @Test
    void documents_that_fill_exactly_64_KiB_are_written_together() throws Exception {
        String first = "id:ns:music::first";
        String half = "id:ns:music::half-" + "x".repeat(32 * 1024 - 32);
        String small = "id:ns:music::small-" + "x".repeat(2_000);
        assertEquals(64 * 1024, 2 * removalJson(half).length());
        List<String> ids = List.of(first, half, half, small);

        List<Integer> expected = List.of(removalJson(first).length() - 1, 64 * 1024, removalJson(small).length(), 0);
        assertRemovals(ids, expected);
    }

    @Test
    void small_documents_are_written_together() throws Exception {
        List<String> ids = List.of("id:ns:music::first", "id:ns:music::second", "id:ns:music::third");
        assertRemovals(ids, List.of(removalJson(ids.get(0)).length() - 1,
                                   removalJson(ids.get(1)).length() + removalJson(ids.get(2)).length(), 0));
    }

    @Test
    void acknowledgements_wait_for_completed_flush() throws Exception {
        assertAcknowledgementsWaitForFlush(CompletionHandler::completed);
    }

    @Test
    void failed_flush_releases_waiting_acknowledgements() throws Exception {
        assertAcknowledgementsWaitForFlush(handler -> handler.failed(new IOException("Client disconnected")));
    }

    private static void assertAcknowledgementsWaitForFlush(Consumer<CompletionHandler> finishFlush) throws Exception {
        List<CompletionHandler> flushes = new ArrayList<>();
        var channel = new RecordingChannel() {
            @Override public void write(ByteBuffer buffer, CompletionHandler handler) {
                if (!buffer.hasRemaining()) flushes.add(handler);
                super.write(buffer, null);
            }
        };
        String id = "id:ns:music::document";
        var acked = new AtomicInteger();
        try (JsonResponse response = newResponse(channel)) {
            CompletionHandler countAck = countAcks(acked);
            for (int i = 0; i < 129; i++)
                response.writeDocumentRemoval(new DocumentId(id), countAck);

            assertEquals(128, acked.get());
            assertEquals(1, flushes.size());
            assertEquals(removalJson(id).substring(1), new String(channel.writes.get(1), StandardCharsets.UTF_8));
            assertEquals(removalJson(id).repeat(127), new String(channel.writes.get(2), StandardCharsets.UTF_8));
            assertEquals(0, channel.writes.get(3).length);

            finishFlush.accept(flushes.get(0));
            assertEquals(129, acked.get());
            response.writeDocumentsArrayEnd();
        }
        int size = removalJson(id).length();
        assertDocumentWrites(channel, List.of(size - 1, 127 * size, 0, size, 0));
        assertEquals(Collections.nCopies(129, id), removedIds(channel));
    }

    // Every document is serialized with a leading comma.
    private static String removalJson(String id) {
        return ",{\"remove\":\"" + id + "\"}";
    }

    private static JsonResponse newResponse(ContentChannel channel) throws Exception {
        HttpRequest request = mock(HttpRequest.class);
        when(request.getUri()).thenReturn(URI.create("http://localhost/document/v1/"));
        JsonResponse response = JsonResponse.createWithPath(request, ignored -> channel, new JsonFormat.EncodeOptions(true, false, false));
        response.commit(200);
        response.writeDocumentsArrayStart();
        return response;
    }

    private static CompletionHandler countAcks(AtomicInteger acked) {
        return new CompletionHandler() {
            @Override public void completed() { acked.incrementAndGet(); }
            @Override public void failed(Throwable t) { throw new AssertionError(t); }
        };
    }

    private static void assertRemovals(List<String> ids, List<Integer> expectedSizes) throws Exception {
        var channel = new RecordingChannel();
        var acked = new AtomicInteger();
        try (JsonResponse response = newResponse(channel)) {
            CompletionHandler countAck = countAcks(acked);
            for (String id : ids)
                response.writeDocumentRemoval(new DocumentId(id), countAck);
            response.writeDocumentsArrayEnd();
        }
        assertEquals(ids.size(), acked.get(), "every document is acknowledged");
        assertDocumentWrites(channel, expectedSizes);
        assertEquals(ids, removedIds(channel));
    }

    private static void assertDocumentWrites(RecordingChannel channel, List<Integer> expectedSizes) {
        assertEquals("{\"pathId\":\"/document/v1/\",\"documents\":[", new String(channel.writes.get(0), StandardCharsets.UTF_8));
        assertEquals("]}", new String(channel.writes.get(channel.writes.size() - 1), StandardCharsets.UTF_8));
        assertEquals(expectedSizes, channel.writes.subList(1, channel.writes.size() - 1).stream().map(write -> write.length).toList());
    }

    private static List<String> removedIds(RecordingChannel channel) throws Exception {
        var all = new ByteArrayOutputStream();
        for (byte[] write : channel.writes) all.write(write);
        Inspector documents = SlimeUtils.jsonToSlime(all.toByteArray()).get().field("documents");
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < documents.entries(); i++) ids.add(documents.entry(i).field("remove").asString());
        return ids;
    }

}
