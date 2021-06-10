// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author jonmv
 */
class HttpFeedClientTest {

    @Test
    void testFeeding() throws ExecutionException, InterruptedException {
        DocumentId id = DocumentId.of("ns", "type", "0");
        AtomicReference<BiFunction<DocumentId, SimpleHttpRequest, CompletableFuture<SimpleHttpResponse>>> dispatch = new AtomicReference<>();
        class MockRequestStrategy implements RequestStrategy {
            @Override public OperationStats stats() { throw new UnsupportedOperationException(); }
            @Override public boolean hasFailed() { return false; }
            @Override public void destroy() { throw new UnsupportedOperationException(); }
            @Override public void await() { throw new UnsupportedOperationException(); }
            @Override public CompletableFuture<SimpleHttpResponse> enqueue(DocumentId documentId, SimpleHttpRequest request) { return dispatch.get().apply(documentId, request); }
        }
        FeedClient client = new HttpFeedClient(FeedClientBuilder.create(URI.create("https://dummy:123")), new MockRequestStrategy());

        // Vespa error is an error result.
        dispatch.set((documentId, request) -> {
            try {
                assertEquals(id, documentId);
                assertEquals("/document/v1/ns/type/docid/0?create=true&condition=false&timeout=5000ms&route=route",
                             request.getUri().toString());
                assertEquals("json", request.getBodyText());

                SimpleHttpResponse response = new SimpleHttpResponse(502);
                response.setBody("{\n" +
                                 "  \"pathId\": \"/document/v1/ns/type/docid/0\",\n" +
                                 "  \"id\": \"id:ns:type::0\",\n" +
                                 "  \"message\": \"Ooops! ... I did it again.\",\n" +
                                 "  \"trace\": \"I played with your heart. Got lost in the game.\"\n" +
                                 "}",
                                 ContentType.APPLICATION_JSON);
                return CompletableFuture.completedFuture(response);
            }
            catch (Throwable thrown) {
                CompletableFuture<SimpleHttpResponse> failed = new CompletableFuture<>();
                failed.completeExceptionally(thrown);
                return failed;
            }
        });
        Result result = client.put(id,
                                   "json",
                                   OperationParameters.empty()
                                                      .createIfNonExistent(true)
                                                      .testAndSetCondition("false")
                                                      .route("route")
                                                      .timeout(Duration.ofSeconds(5)))
                              .get();
        assertEquals("Ooops! ... I did it again.", result.resultMessage().get());
        assertEquals("I played with your heart. Got lost in the game.", result.traceMessage().get());


        // Handler error is a FeedException.
        dispatch.set((documentId, request) -> {
            try {
                assertEquals(id, documentId);
                assertEquals("/document/v1/ns/type/docid/0",
                             request.getUri().toString());
                assertEquals("json", request.getBodyText());

                SimpleHttpResponse response = new SimpleHttpResponse(500);
                response.setBody("{\n" +
                                 "  \"pathId\": \"/document/v1/ns/type/docid/0\",\n" +
                                 "  \"id\": \"id:ns:type::0\",\n" +
                                 "  \"message\": \"Alla ska i jorden.\",\n" +
                                 "  \"trace\": \"Din tid den kom, och senn så for den. \"\n" +
                                 "}",
                                 ContentType.APPLICATION_JSON);
                return CompletableFuture.completedFuture(response);
            }
            catch (Throwable thrown) {
                CompletableFuture<SimpleHttpResponse> failed = new CompletableFuture<>();
                failed.completeExceptionally(thrown);
                return failed;
            }
        });
        ExecutionException expected = assertThrows(ExecutionException.class,
                                                   () -> client.put(id,
                                                                    "json",
                                                                    OperationParameters.empty())
                                                               .get());
        assertEquals("Status 500 executing 'POST /document/v1/ns/type/docid/0': Alla ska i jorden.", expected.getCause().getMessage());
    }

}
