// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
            SimpleClient feedClient = new SimpleClient();
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
            assertEquals(docs + 1, feedClient.ids.size());
            assertEquals(docs + 1, resultsReceived.get());
            assertTrue(completedSuccessfully.get());
            assertNull(exceptionThrow.get());
        }
    }

    @Test
    public void singleJsonOperationIsDispatchedToFeedClient() throws IOException, ExecutionException, InterruptedException {
        try (JsonFeeder feeder = JsonFeeder.builder(new SimpleClient()).build()) {
            String json = "{\"put\": \"id:ns:type::abc1\",\n" +
                    "    \"fields\": {\n" +
                    "      \"lul\":\"lal\"\n" +
                    "    }\n" +
                    "  }\n";
            Result result = feeder.feedSingle(json).get();
            assertEquals(DocumentId.of("id:ns:type::abc1"), result.documentId());
            assertEquals(Result.Type.success, result.type());
            assertEquals("success", result.resultMessage().get());
        }
    }

    private static class SimpleClient implements FeedClient {
        final Set<String> ids = new HashSet<>();

        @Override
        public CompletableFuture<Result> put(DocumentId documentId, String documentJson, OperationParameters params) {
            ids.add(documentId.userSpecific());
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
        public void close(boolean graceful) { }

        private CompletableFuture<Result> createSuccessResult(DocumentId documentId) {
            return CompletableFuture.completedFuture(new Result(Result.Type.success, documentId, "success", null));
        }
    }

}
