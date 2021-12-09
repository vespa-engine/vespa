// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client.examples;

import ai.vespa.feed.client.DocumentId;
import ai.vespa.feed.client.FeedClient;
import ai.vespa.feed.client.FeedClientBuilder;
import ai.vespa.feed.client.OperationParameters;
import ai.vespa.feed.client.Result;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

class SimpleExample {

    public static void main(String[] args) {
        try (FeedClient client = FeedClientBuilder.create(URI.create("https://my-container-endpoint-with-http2:8080/")).build()) {
            DocumentId id = DocumentId.of("namespace", "documenttype", "1");
            String json = "{\"fields\": {\"title\": \"hello world\"}}";
            OperationParameters params = OperationParameters.empty()
                    .timeout(Duration.ofSeconds(5))
                    .route("myvesparoute");
            CompletableFuture<Result> promise = client.put(id, json, params);
            promise.whenComplete(((result, throwable) -> {
                if (throwable != null) {
                    throwable.printStackTrace();
                } else {
                    System.out.printf("'%s' for document '%s': %s%n", result.type(), result.documentId(), result.resultMessage());
                }
            }));
        }
    }

}
