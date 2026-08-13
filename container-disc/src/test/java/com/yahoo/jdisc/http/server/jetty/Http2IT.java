// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import ai.vespa.util.http.hc5.VespaTlsStrategy;
import com.yahoo.container.logging.ConnectionLog;
import com.yahoo.container.logging.ConnectionLogEntry;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.ServerConfig;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.async.methods.SimpleResponseConsumer;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.H2AsyncClientBuilder;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.net.URIAuthority;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.yahoo.jdisc.Response.Status.OK;
import static com.yahoo.jdisc.http.server.jetty.Utils.createHttp2Client;
import static com.yahoo.jdisc.http.server.jetty.Utils.createSslTestDriver;
import static com.yahoo.jdisc.http.server.jetty.Utils.generatePrivateKeyAndCertificate;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author bjorncs
 */
class Http2IT {
    @Test
    void requireThatServerCanRespondToHttp2Request(@TempDir Path tmpFolder) throws Exception {
        Path privateKeyFile = tmpFolder.resolve("private-key.pem");
        Path certificateFile = tmpFolder.resolve("certificate.pem");
        generatePrivateKeyAndCertificate(privateKeyFile, certificateFile);

        MetricConsumerMock metricConsumer = new MetricConsumerMock();
        InMemoryConnectionLog connectionLog = new InMemoryConnectionLog();
        JettyTestDriver driver = createSslTestDriver(certificateFile, privateKeyFile, metricConsumer, connectionLog);
        try (CloseableHttpAsyncClient client = createHttp2Client(driver)) {
            String uri = "https://localhost:" + driver.server().getListenPort() + "/status.html";
            SimpleHttpResponse response = client.execute(SimpleRequestBuilder.get(uri).build(), null).get();
            assertNull(response.getBodyText());
            assertEquals(OK, response.getCode());
        }
        assertTrue(driver.close());
        ConnectionLogEntry entry = connectionLog.logEntries().get(0);
        assertEquals("HTTP/2.0", entry.httpProtocol().get());
    }

    @Test
    void requireThatServerCanRespondToHttp2PlainTextRequest() throws Exception {
        InMemoryConnectionLog connectionLog = new InMemoryConnectionLog();
        JettyTestDriver driver = JettyTestDriver.newConfiguredInstance(
                new EchoRequestHandler(),
                new ServerConfig.Builder().connectionLog(new ServerConfig.ConnectionLog.Builder().enabled(true)),
                new ConnectorConfig.Builder(),
                binder -> binder.bind(ConnectionLog.class).toInstance(connectionLog));
        try (CloseableHttpAsyncClient client = createHttp2Client(driver)) {
            String uri = "http://localhost:" + driver.server().getListenPort() + "/status.html";
            SimpleHttpResponse response = client.execute(SimpleRequestBuilder.get(uri).build(), null).get();
            assertNull(response.getBodyText());
            assertEquals(OK, response.getCode());
        }
        assertTrue(driver.close());
        ConnectionLogEntry entry = connectionLog.logEntries().get(0);
        assertEquals("HTTP/2.0", entry.httpProtocol().get());
    }

    @Test
    void requireThatServerAcceptsEmptyAuthority(@TempDir Path tmpFolder) throws IOException, ExecutionException, InterruptedException {
        Path privateKeyFile = tmpFolder.resolve("private-key.pem");
        Path certificateFile = tmpFolder.resolve("certificate.pem");
        generatePrivateKeyAndCertificate(privateKeyFile, certificateFile);

        var metricConsumer = new MetricConsumerMock();
        var connectionLog = new InMemoryConnectionLog();
        var driver = createSslTestDriver(certificateFile, privateKeyFile, metricConsumer, connectionLog);
        var tlsStrategy = VespaTlsStrategy.tlsStrategyBuilder()
                .setSslContext(driver.sslContext())
                .buildAsync();
        try (var client = H2AsyncClientBuilder.create()
                .setTlsStrategy(tlsStrategy)
                .disableAutomaticRetries()
                // Specify blank :authority pseudo-header
                .addRequestInterceptorLast((request, entity, ctx) -> request.setAuthority(new URIAuthority("")))
                .build()) {
            client.start();
            var req = new SimpleHttpRequest("GET", URI.create("https://localhost:" + driver.server().getListenPort() + "/"));
            var response = client.execute(req, null).get();
            assertEquals(200, response.getCode());
        }
        assertTrue(driver.close());
    }

    @Test
    void stalledRequestContentOnHttp2StreamFailsWith408() throws Exception {
        var handler = new BlockingContentReadRequestHandler();
        JettyTestDriver driver = JettyTestDriver.newConfiguredInstance(
                handler,
                new ServerConfig.Builder(),
                // Connection-level idleTimeout is left at its default (180s), so the stalled stream
                // must be failed by the stream idle timeout, as a multiplexed connection may be kept
                // alive by other healthy streams.
                new ConnectorConfig.Builder()
                        .http2(new ConnectorConfig.Http2.Builder().streamIdleTimeout(0.5)));
        try (CloseableHttpAsyncClient client = createHttp2Client(driver)) {
            var request = new BasicHttpRequest(
                    "POST", URI.create("http://localhost:" + driver.server().getListenPort() + "/"));
            // Announce a body, send only part of it, then leave the stream stalled without closing it.
            var response = client.execute(
                            new BasicRequestProducer(request, new StallingEntityProducer("partial", 100)),
                            SimpleResponseConsumer.create(), null)
                    .get(30, TimeUnit.SECONDS);
            assertEquals(408, response.getCode());
        }
        assertTrue(handler.contentReadTerminated.await(30, TimeUnit.SECONDS),
                   "Handler thread was not released from blocking content read");
        assertTrue(driver.close());
    }

    /** Produces part of the announced request content, then stalls without ever completing the stream. */
    private static class StallingEntityProducer implements AsyncEntityProducer {

        private final byte[] partialContent;
        private final long contentLength;
        private final AtomicBoolean produced = new AtomicBoolean();

        StallingEntityProducer(String partialContent, long contentLength) {
            this.partialContent = partialContent.getBytes(UTF_8);
            this.contentLength = contentLength;
        }

        @Override public boolean isRepeatable() { return false; }
        @Override public void failed(Exception cause) {}
        @Override public long getContentLength() { return contentLength; }
        @Override public String getContentType() { return "application/octet-stream"; }
        @Override public String getContentEncoding() { return null; }
        @Override public boolean isChunked() { return false; }
        @Override public Set<String> getTrailerNames() { return Set.of(); }
        @Override public int available() { return produced.get() ? 0 : partialContent.length; }
        @Override public void releaseResources() {}

        @Override
        public void produce(DataStreamChannel channel) throws IOException {
            if (produced.compareAndSet(false, true)) channel.write(ByteBuffer.wrap(partialContent));
            // Never write the remaining content and never end the stream
        }
    }
}
