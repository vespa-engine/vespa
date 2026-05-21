// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client.impl;

import ai.vespa.feed.client.DocumentId;
import ai.vespa.feed.client.FeedClient;
import ai.vespa.feed.client.FeedClientBuilder;
import ai.vespa.feed.client.OperationParameters;
import ai.vespa.feed.client.OperationStats;
import ai.vespa.feed.client.Result;
import ai.vespa.feed.client.ResultException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author jonmv
 */
class CliClientTest {

    private Supplier<FeedClientBuilder> originalSupplier;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void saveOriginalFeedClientBuilderSupplier() throws Exception {
        var helperClass = Class.forName("ai.vespa.feed.client.Helper");
        var field = helperClass.getDeclaredField("feedClientBuilderSupplier");
        field.setAccessible(true);
        var atomicRef = (java.util.concurrent.atomic.AtomicReference<Supplier<FeedClientBuilder>>) field.get(null);
        originalSupplier = atomicRef.get();
    }

    @AfterEach
    void restoreFeedClientBuilderSupplier() {
        FeedClientBuilder.setFeedClientBuilderSupplier(originalSupplier);
    }

    @Test
    void testDummyStream() throws IOException {
        AtomicInteger count = new AtomicInteger(3);
        InputStream in = CliClient.createDummyInputStream(4, new Random(0), () -> count.decrementAndGet() >= 0);
        byte[] buffer = new byte[1 << 20];
        int offset = 0, read;
        while ((read = in.read(buffer, offset, buffer.length - offset)) >= 0) offset += read;
        assertEquals("{ \"put\": \"id:test:test::ssxvnjhp\", \"fields\": { \"test\": \"dqdx\" } }\n" +
                     "{ \"put\": \"id:test:test::vcrastvy\", \"fields\": { \"test\": \"bcwv\" } }\n" +
                     "{ \"put\": \"id:test:test::mgnykrxv\", \"fields\": { \"test\": \"zxkg\" } }\n",
                     new String(buffer, 0, offset, StandardCharsets.UTF_8));
    }

    @Test
    void exit_code_is_zero_without_flag_even_when_there_are_feed_failures() throws Exception {
        FeedClientBuilder.setFeedClientBuilderSupplier(() -> new StubFeedClientBuilder(false));
        int exitCode = runCli("--endpoint", "https://localhost:8080", "--stdin");
        assertEquals(0, exitCode);
    }

    @Test
    void exit_code_is_one_with_flag_when_there_are_feed_failures() throws Exception {
        FeedClientBuilder.setFeedClientBuilderSupplier(() -> new StubFeedClientBuilder(false));
        int exitCode = runCli("--endpoint", "https://localhost:8080", "--stdin", "--exit-on-feed-errors");
        assertEquals(1, exitCode);
    }

    @Test
    void exit_code_is_zero_with_flag_when_there_are_no_feed_failures() throws Exception {
        FeedClientBuilder.setFeedClientBuilderSupplier(() -> new StubFeedClientBuilder(true));
        int exitCode = runCli("--endpoint", "https://localhost:8080", "--stdin", "--exit-on-feed-errors");
        assertEquals(0, exitCode);
    }

    private static int runCli(String... args) throws Exception {
        String feed = "[ { \"put\": \"id:test:test::doc1\", \"fields\": { \"test\": \"value\" } } ]";
        InputStream stdin = new ByteArrayInputStream(feed.getBytes(StandardCharsets.UTF_8));
        PrintStream devNull = new PrintStream(OutputStream.nullOutputStream());
        CliClient client = newCliClient(devNull, devNull, stdin);
        Method run = CliClient.class.getDeclaredMethod("run", String[].class);
        run.setAccessible(true);
        return (int) run.invoke(client, (Object) args);
    }

    private static CliClient newCliClient(PrintStream out, PrintStream err, InputStream in) throws Exception {
        Constructor<CliClient> ctor = CliClient.class.getDeclaredConstructor(PrintStream.class, PrintStream.class, InputStream.class);
        ctor.setAccessible(true);
        return ctor.newInstance(out, err, in);
    }

    static class StubFeedClientBuilder implements FeedClientBuilder {

        private final boolean succeed;

        StubFeedClientBuilder(boolean succeed) {
            this.succeed = succeed;
        }

        @Override public FeedClient build() { return new StubFeedClient(succeed); }
        @Override public FeedClientBuilder setEndpointUris(List<URI> endpoints) { return this; }
        @Override public FeedClientBuilder setConnectionsPerEndpoint(int max) { return this; }
        @Override public FeedClientBuilder setMaxStreamPerConnection(int max) { return this; }
        @Override public FeedClientBuilder setConnectionTimeToLive(Duration ttl) { return this; }
        @Override public FeedClientBuilder setSslContext(javax.net.ssl.SSLContext context) { return this; }
        @Override public FeedClientBuilder setHostnameVerifier(javax.net.ssl.HostnameVerifier verifier) { return this; }
        @Override public FeedClientBuilder setProxyHostnameVerifier(javax.net.ssl.HostnameVerifier verifier) { return this; }
        @Override public FeedClientBuilder noBenchmarking() { return this; }
        @Override public FeedClientBuilder addRequestHeader(String name, String value) { return this; }
        @Override public FeedClientBuilder addRequestHeader(String name, Supplier<String> valueSupplier) { return this; }
        @Override public FeedClientBuilder addProxyRequestHeader(String name, String value) { return this; }
        @Override public FeedClientBuilder addProxyRequestHeader(String name, Supplier<String> valueSupplier) { return this; }
        @Override public FeedClientBuilder setRetryStrategy(FeedClient.RetryStrategy strategy) { return this; }
        @Override public FeedClientBuilder setCircuitBreaker(FeedClient.CircuitBreaker breaker) { return this; }
        @Override public FeedClientBuilder setCertificate(Path certificatePemFile, Path privateKeyPemFile) { return this; }
        @Override public FeedClientBuilder setCertificate(Collection<X509Certificate> certificate, PrivateKey privateKey) { return this; }
        @Override public FeedClientBuilder setCertificate(X509Certificate certificate, PrivateKey privateKey) { return this; }
        @Override public FeedClientBuilder setDryrun(boolean enabled) { return this; }
        @Override public FeedClientBuilder setSpeedTest(boolean enabled) { return this; }
        @Override public FeedClientBuilder setCaCertificatesFile(Path caCertificatesFile) { return this; }
        @Override public FeedClientBuilder setProxyCaCertificatesFile(Path caCertificatesFile) { return this; }
        @Override public FeedClientBuilder setCaCertificates(Collection<X509Certificate> caCertificates) { return this; }
        @Override public FeedClientBuilder setProxyCaCertificates(Collection<X509Certificate> caCertificates) { return this; }
        @Override public FeedClientBuilder setProxy(URI uri) { return this; }
        @Override public FeedClientBuilder setCompression(FeedClientBuilder.Compression compression) { return this; }
        @Override public FeedClientBuilder setInitialInflightFactor(int factor) { return this; }
    }

    static class StubFeedClient implements FeedClient {

        private final boolean succeed;

        StubFeedClient(boolean succeed) {
            this.succeed = succeed;
        }

        @Override
        public CompletableFuture<Result> put(DocumentId documentId, String documentJson, OperationParameters params) {
            CompletableFuture<Result> f = new CompletableFuture<>();
            if (succeed) {
                f.complete(new StubResult(documentId));
            } else {
                f.completeExceptionally(new ResultException(documentId, "mocked feed failure", null));
            }
            return f;
        }

        @Override
        public CompletableFuture<Result> update(DocumentId documentId, String updateJson, OperationParameters params) {
            return put(documentId, updateJson, params);
        }

        @Override
        public CompletableFuture<Result> remove(DocumentId documentId, OperationParameters params) {
            return put(documentId, null, params);
        }

        @Override
        public OperationStats stats() {
            return new OperationStats(0, 0, 0, 0, 0, 0, 0, 0, 0, Map.of());
        }

        @Override public void resetStats() { }
        @Override public FeedClient.CircuitBreaker.State circuitBreakerState() { return FeedClient.CircuitBreaker.State.CLOSED; }
        @Override public void close(boolean graceful) { }
    }

    static class StubResult implements Result {

        private final DocumentId documentId;

        StubResult(DocumentId documentId) {
            this.documentId = documentId;
        }

        @Override public Result.Type type() { return Result.Type.success; }
        @Override public DocumentId documentId() { return documentId; }
        @Override public Optional<String> resultMessage() { return Optional.empty(); }
        @Override public Optional<String> traceMessage() { return Optional.empty(); }
    }

}
