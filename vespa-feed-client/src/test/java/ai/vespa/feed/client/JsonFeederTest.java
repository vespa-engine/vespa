// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonFeederTest {

    @Test
    void test() throws IOException {
        int docs = 1 << 14;
        String json = "[\n" +

                      IntStream.range(0, docs).mapToObj(i ->
                                                                "  {\n" +
                                                                "    \"id\": \"id:ns:type::abc" + i + "\",\n" +
                                                                "    \"fields\": {\n" +
                                                                "      \"lul\":\"lal\"\n" +
                                                                "    }\n" +
                                                                "  },\n"
                      ).collect(joining()) +

                      "  {\n" +
                      "    \"id\": \"id:ns:type::abc" + docs + "\",\n" +
                      "    \"fields\": {\n" +
                      "      \"lul\":\"lal\"\n" +
                      "    }\n" +
                      "  }\n" +
                      "]";
        AtomicReference<FeedException> exceptionThrow = new AtomicReference<>();
        Path tmpFile = Files.createTempFile(null, null);
        Files.write(tmpFile, json.getBytes(UTF_8));
        try (InputStream in = Files.newInputStream(tmpFile, StandardOpenOption.READ, StandardOpenOption.DELETE_ON_CLOSE)) {
            AtomicInteger resultsReceived = new AtomicInteger();
            AtomicBoolean completedSuccessfully = new AtomicBoolean();
            long startNanos = System.nanoTime();
            MockClient feedClient = new MockClient();
            JsonFeeder.builder(feedClient).build()
                    .feedMany(in, 1 << 7,
                            new JsonFeeder.ResultCallback() { // TODO: hangs when buffer is smaller than largest document
                                @Override
                                public void onNextResult(Result result, FeedException error) { resultsReceived.incrementAndGet(); }

                                @Override
                                public void onError(FeedException error) { exceptionThrow.set(error); }

                                @Override
                                public void onComplete() { completedSuccessfully.set(true); }
                            })
                    .join();

            System.err.println((json.length() / 1048576.0) + " MB in " + (System.nanoTime() - startNanos) * 1e-9 + " seconds");
            assertEquals(docs + 1, feedClient.putOperations.size());
            assertEquals(docs + 1, resultsReceived.get());
            assertTrue(completedSuccessfully.get());
            assertNull(exceptionThrow.get());
        }
    }

    @Test
    public void multipleJsonArrayOperationsAreDispatchedToFeedClient() throws IOException, ExecutionException, InterruptedException {
        MockClient client = new MockClient();
        try (JsonFeeder feeder = JsonFeeder.builder(client).build()) {
            String json = "[{" +
                          "  \"put\": \"id:ns:type::abc1\",\n" +
                          "  \"fields\": {\n" +
                          "    \"lul\":\"lal\"\n" +
                          "  }\n" +
                          "},\n" +
                          "{" +
                          "  \"put\": \"id:ns:type::abc2\",\n" +
                          "  \"fields\": {\n" +
                          "    \"lul\":\"lal\"\n" +
                          "  }\n" +
                          "}]\n";
            feeder.feedMany(new ByteArrayInputStream(json.getBytes(UTF_8))).get();
            client.assertPutDocumentIds("abc1", "abc2");
            client.assertPutOperation("abc1", "{\"fields\":{\n    \"lul\":\"lal\"\n  }}");
            client.assertPutOperation("abc2", "{\"fields\":{\n    \"lul\":\"lal\"\n  }}");
        }
    }

    @Test
    public void multipleJsonLOperationsAreDispatchedToFeedClient() throws IOException, ExecutionException, InterruptedException {
        MockClient client = new MockClient();
        try (JsonFeeder feeder = JsonFeeder.builder(client).build()) {
            String json = "{" +
                          "  \"put\": \"id:ns:type::abc1\",\n" +
                          "  \"fields\": {\n" +
                          "    \"lul\":\"lal\"\n" +
                          "  }\n" +
                          "}\n" +
                          "{" +
                          "  \"put\": \"id:ns:type::abc2\",\n" +
                          "  \"fields\": {\n" +
                          "    \"lul\":\"lal\"\n" +
                          "  }\n" +
                          "}\n";
            feeder.feedMany(new ByteArrayInputStream(json.getBytes(UTF_8))).get();
            client.assertPutDocumentIds("abc1", "abc2");
            client.assertPutOperation("abc1", "{\"fields\":{\n    \"lul\":\"lal\"\n  }}");
            client.assertPutOperation("abc2", "{\"fields\":{\n    \"lul\":\"lal\"\n  }}");
        }
    }

    @Test
    public void singleJsonOperationIsDispatchedToFeedClient() throws IOException, ExecutionException, InterruptedException {
        MockClient client = new MockClient();
        try (JsonFeeder feeder = JsonFeeder.builder(client).build()) {
            String json = "{\"put\": \"id:ns:type::abc1\",\n" +
                    "    \"fields\": {\n" +
                    "      \"lul\":\"lal\"\n" +
                    "    }\n" +
                    "  }\n";
            Result result = feeder.feedSingle(json).get();
            assertEquals(DocumentId.of("id:ns:type::abc1"), result.documentId());
            assertEquals(Result.Type.success, result.type());
            assertEquals("success", result.resultMessage().get());
            client.assertPutOperation("abc1", "{\"fields\":{\n      \"lul\":\"lal\"\n    }}");
        }
    }

    private static class MockClient implements FeedClient {
        final Map<DocumentId, String> putOperations = new LinkedHashMap<>();

        @Override
        public CompletableFuture<Result> put(DocumentId documentId, String documentJson, OperationParameters params) {
            putOperations.put(documentId, documentJson);
            return createSuccessResult(documentId);
        }

        @Override
        public CompletableFuture<Result> update(DocumentId documentId, String updateJson, OperationParameters params) {
            return createSuccessResult(documentId);
        }

        @Override
        public CompletableFuture<Result> remove(DocumentId documentId, OperationParameters params) {
            return createSuccessResult(documentId);
        }

        @Override
        public OperationStats stats() { return null; }

        @Override
        public CircuitBreaker.State circuitBreakerState() { return null; }

        @Override
        public void close(boolean graceful) { }

        private CompletableFuture<Result> createSuccessResult(DocumentId documentId) {
            return CompletableFuture.completedFuture(new Result(Result.Type.success, documentId, "success", null));
        }

        void assertPutDocumentIds(String... expectedUserSpecificIds) {
            List<String> expected = Arrays.stream(expectedUserSpecificIds)
                    .map(userSpecific -> "id:ns:type::" + userSpecific)
                    .sorted()
                    .collect(Collectors.toList());
            List<String> actual = putOperations.keySet().stream()
                    .map(DocumentId::toString).sorted()
                    .collect(Collectors.toList());
            assertEquals(expected, actual, "Document ids must match");
        }

        void assertPutOperation(String userSpecificId, String expectedJson) {
            DocumentId docId = DocumentId.of("id:ns:type::" + userSpecificId);
            String json = putOperations.get(docId);
            assertNotNull(json);
            assertEquals(expectedJson.trim(), json.trim());
        }
    }

}
