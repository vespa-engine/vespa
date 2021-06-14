// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author jonmv
 */
class HttpFeedClientTest {

    @Test
    void testFeeding() throws ExecutionException, InterruptedException {
        DocumentId id = DocumentId.of("ns", "type", "0");
        AtomicReference<BiFunction<DocumentId, HttpRequest, CompletableFuture<HttpResponse>>> dispatch = new AtomicReference<>();
        class MockRequestStrategy implements RequestStrategy {
            @Override public OperationStats stats() { throw new UnsupportedOperationException(); }
            @Override public FeedClient.CircuitBreaker.State circuitBreakerState() { return FeedClient.CircuitBreaker.State.CLOSED; }
            @Override public void destroy() { throw new UnsupportedOperationException(); }
            @Override public void await() { throw new UnsupportedOperationException(); }
            @Override public CompletableFuture<HttpResponse> enqueue(DocumentId documentId, HttpRequest request) { return dispatch.get().apply(documentId, request); }
        }
        FeedClient client = new HttpFeedClient(FeedClientBuilder.create(URI.create("https://dummy:123")), new MockRequestStrategy());

        // Vespa error is an error result.
        dispatch.set((documentId, request) -> {
            try {
                assertEquals(id, documentId);
                assertEquals("/document/v1/ns/type/docid/0?create=true&condition=false&timeout=5000ms&route=route",
                             request.path());
                assertEquals("json", new String(request.body(), UTF_8));

                HttpResponse response = HttpResponse.of(502,
                                                        ("{\n" +
                                                         "  \"pathId\": \"/document/v1/ns/type/docid/0\",\n" +
                                                         "  \"id\": \"id:ns:type::0\",\n" +
                                                         "  \"message\": \"Ooops! ... I did it again.\",\n" +
                                                         "  \"trace\": \"I played with your heart. Got lost in the game.\"\n" +
                                                         "}").getBytes(UTF_8));
                return CompletableFuture.completedFuture(response);
            }
            catch (Throwable thrown) {
                CompletableFuture<HttpResponse> failed = new CompletableFuture<>();
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
                             request.path());
                assertEquals("json", new String(request.body(), UTF_8));

                HttpResponse response = HttpResponse.of(500,
                                                        ("{\n" +
                                                         "  \"pathId\": \"/document/v1/ns/type/docid/0\",\n" +
                                                         "  \"id\": \"id:ns:type::0\",\n" +
                                                         "  \"message\": \"Alla ska i jorden.\",\n" +
                                                         "  \"trace\": \"Din tid den kom, och senn så for den. \"\n" +
                                                         "}").getBytes(UTF_8));
                return CompletableFuture.completedFuture(response);
            }
            catch (Throwable thrown) {
                CompletableFuture<HttpResponse> failed = new CompletableFuture<>();
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
