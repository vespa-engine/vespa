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
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

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

        var channel = new RecordingChannel();
        var acked = writeRemovals(channel, ids);

        // An empty write after each batch lets more documents be acknowledged. The first document is written alone.
        List<Integer> expected = List.of(size - 1, 32 * size, 32 * size, 32 * size, 31 * size, 0,
                                         32 * size, 32 * size, 32 * size, 32 * size, 0,
                                         32 * size, 12 * size, 0);
        assertEquals(expected, documentAndAcknowledgementWrites(channel));
        assertEquals(ids.size(), acked.get(), "every document is acknowledged");
        assertEquals(ids, removedIds(channel));
    }

    @Test
    void a_document_larger_than_64_KiB_is_written_alone() throws Exception {
        String small = "id:ns:music::small-" + "x".repeat(2_000);
        String large = "id:ns:music::large-" + "x".repeat(65_511); // near the maximum document id length
        assertEquals(64 * 1024 + 8, removalJson(large).length());
        List<String> ids = List.of(small, small, large, small);

        var channel = new RecordingChannel();
        writeRemovals(channel, ids);

        int smallSize = removalJson(small).length();
        List<Integer> expected = List.of(smallSize - 1, smallSize, removalJson(large).length(), smallSize, 0);
        assertEquals(expected, documentAndAcknowledgementWrites(channel));
        assertEquals(ids, removedIds(channel));
    }

    @Test
    void documents_that_fill_exactly_64_KiB_are_written_together() throws Exception {
        String first = "id:ns:music::first";
        String half = "id:ns:music::half-" + "x".repeat(32 * 1024 - 32); // two of these make 64 KiB of JSON
        String small = "id:ns:music::small-" + "x".repeat(2_000);
        assertEquals(64 * 1024, 2 * removalJson(half).length());
        List<String> ids = List.of(first, half, half, small);

        var channel = new RecordingChannel();
        writeRemovals(channel, ids);

        List<Integer> expected = List.of(64 * 1024, removalJson(small).length(), 0);
        assertEquals(expected, documentAndAcknowledgementWrites(channel));
        assertEquals(ids, removedIds(channel));
    }

    /** The JSON of a removal, with the comma that comes before every document but the first. */
    private static String removalJson(String id) {
        return ",{\"remove\":\"" + id + "\"}";
    }

    /** Writes a visit response with these removals, and returns the number of acknowledged documents. */
    private static AtomicInteger writeRemovals(ContentChannel channel, List<String> ids) throws Exception {
        HttpRequest request = mock(HttpRequest.class);
        when(request.getUri()).thenReturn(URI.create("http://localhost/document/v1/"));
        JsonResponse response = JsonResponse.createWithPath(request, ignored -> channel, new JsonFormat.EncodeOptions(true, false, false));
        response.commit(200);
        response.writeDocumentsArrayStart();
        var acked = new AtomicInteger();
        CompletionHandler countAck = new CompletionHandler() {
            @Override public void completed() { acked.incrementAndGet(); }
            @Override public void failed(Throwable t) { throw new AssertionError(t); }
        };
        for (String id : ids)
            response.writeDocumentRemoval(new DocumentId(id), countAck);
        response.writeDocumentsArrayEnd();
        response.close();
        return acked;
    }

    /** Sizes of the document writes (over 1000 bytes) and of the empty acknowledgement writes. */
    private static List<Integer> documentAndAcknowledgementWrites(RecordingChannel channel) {
        return channel.writes.stream().map(write -> write.length).filter(length -> length == 0 || length > 1000).toList();
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
