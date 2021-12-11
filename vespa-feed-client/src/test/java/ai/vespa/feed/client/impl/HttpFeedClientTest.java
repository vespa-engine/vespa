// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client.impl;

import ai.vespa.feed.client.DocumentId;
import ai.vespa.feed.client.FeedClient;
import ai.vespa.feed.client.FeedClientBuilder;
import ai.vespa.feed.client.HttpResponse;
import ai.vespa.feed.client.OperationParameters;
import ai.vespa.feed.client.OperationStats;
import ai.vespa.feed.client.Result;
import ai.vespa.feed.client.ResultException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        FeedClient client = new HttpFeedClient(new FeedClientBuilderImpl(Collections.singletonList(URI.create("https://dummy:123"))), new MockRequestStrategy());

        // Update is a PUT, and 200 OK is a success.
        dispatch.set((documentId, request) -> {
            try {
                assertEquals(id, documentId);
                assertEquals("/document/v1/ns/type/docid/0",
                             request.path());
                assertEquals("PUT", request.method());
                assertEquals("json", new String(request.body(), UTF_8));

                HttpResponse response = HttpResponse.of(200,
                                                        ("{\n" +
                                                         "  \"pathId\": \"/document/v1/ns/type/docid/0\",\n" +
                                                         "  \"id\": \"id:ns:type::0\"\n" +
                                                         "}").getBytes(UTF_8));
                return CompletableFuture.completedFuture(response);
            }
            catch (Throwable thrown) {
                CompletableFuture<HttpResponse> failed = new CompletableFuture<>();
                failed.completeExceptionally(thrown);
                return failed;
            }
        });
        Result result = client.update(id,
                                      "json",
                                      OperationParameters.empty())
                              .get();
        assertEquals(Result.Type.success, result.type());
        assertEquals(id, result.documentId());
        assertEquals(Optional.empty(), result.resultMessage());
        assertEquals(Optional.empty(), result.traceMessage());

        // Remove is a DELETE, and 412 OK is a conditionNotMet.
        dispatch.set((documentId, request) -> {
            try {
                assertEquals(id, documentId);
                assertEquals("/document/v1/ns/type/docid/0?tracelevel=1",
                             request.path());
                assertEquals("DELETE", request.method());
                assertNull(request.body());

                HttpResponse response = HttpResponse.of(412,
                                                        ("{\n" +
                                                         "  \"pathId\": \"/document/v1/ns/type/docid/0\",\n" +
                                                         "  \"id\": \"id:ns:type::0\",\n" +
                                                         "  \"message\": \"Relax, take it easy.\",\n" +
                                                         "  \"trace\": [\n" +
                                                         "    {\n" +
                                                         "      \"message\": \"For there is nothing that we can do.\"\n" +
                                                         "    },\n" +
                                                         "    {\n" +
                                                         "      \"fork\": [\n" +
                                                         "        {\n" +
                                                         "          \"message\": \"Relax, take is easy.\"\n" +
                                                         "        },\n" +
                                                         "        {\n" +
                                                         "          \"message\": \"Blame it on me or blame it on you.\"\n" +
                                                         "        }\n" +
                                                         "      ]\n" +
                                                         "    }\n" +
                                                         "  ]\n" +
                                                         "}").getBytes(UTF_8));
                return CompletableFuture.completedFuture(response);
            }
            catch (Throwable thrown) {
                CompletableFuture<HttpResponse> failed = new CompletableFuture<>();
                failed.completeExceptionally(thrown);
                return failed;
            }
        });
        result = client.remove(id,
                               OperationParameters.empty().tracelevel(1))
                       .get();
        assertEquals(Result.Type.conditionNotMet, result.type());
        assertEquals(id, result.documentId());
        assertEquals(Optional.of("Relax, take it easy."), result.resultMessage());
        assertEquals(Optional.of("[\n" +
                                 "    {\n" +
                                 "      \"message\": \"For there is nothing that we can do.\"\n" +
                                 "    },\n" +
                                 "    {\n" +
                                 "      \"fork\": [\n" +
                                 "        {\n" +
                                 "          \"message\": \"Relax, take is easy.\"\n" +
                                 "        },\n" +
                                 "        {\n" +
                                 "          \"message\": \"Blame it on me or blame it on you.\"\n" +
                                 "        }\n" +
                                 "      ]\n" +
                                 "    }\n" +
                                 "  ]"), result.traceMessage());

        // Put is a POST, and a Vespa error is a ResultException.
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
                                                         "  \"trace\": [ { \"message\": \"I played with your heart. Got lost in the game.\" } ]\n" +
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
                                                   () ->  client.put(id,
                                                                     "json",
                                                                     OperationParameters.empty()
                                                                                        .createIfNonExistent(true)
                                                                                        .testAndSetCondition("false")
                                                                                        .route("route")
                                                                                        .timeout(Duration.ofSeconds(5)))
                                                                .get());
        assertTrue(expected.getCause() instanceof ResultException);
        assertEquals("Ooops! ... I did it again.", expected.getCause().getMessage());
        assertEquals("[ { \"message\": \"I played with your heart. Got lost in the game.\" } ]", ((ResultException) expected.getCause()).getTrace().get());


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
                                                         "  \"trace\": [ { \"message\": \"Din tid den kom, och senn så for den.\" } ]\n" +
                                                         "}").getBytes(UTF_8));
                return CompletableFuture.completedFuture(response);
            }
            catch (Throwable thrown) {
                CompletableFuture<HttpResponse> failed = new CompletableFuture<>();
                failed.completeExceptionally(thrown);
                return failed;
            }
        });
        expected = assertThrows(ExecutionException.class,
                                () -> client.put(id,
                                                 "json",
                                                 OperationParameters.empty())
                                            .get());
        assertEquals("Status 500 executing 'POST /document/v1/ns/type/docid/0': Alla ska i jorden.", expected.getCause().getMessage());
    }

}
