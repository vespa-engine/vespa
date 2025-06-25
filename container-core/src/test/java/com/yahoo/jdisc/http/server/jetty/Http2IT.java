// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.container.logging.ConnectionLog;
import com.yahoo.container.logging.ConnectionLogEntry;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.ServerConfig;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.H2AsyncClientBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.net.URIAuthority;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

import static com.yahoo.jdisc.Response.Status.OK;
import static com.yahoo.jdisc.http.server.jetty.Utils.createHttp2Client;
import static com.yahoo.jdisc.http.server.jetty.Utils.createSslTestDriver;
import static com.yahoo.jdisc.http.server.jetty.Utils.generatePrivateKeyAndCertificate;
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
        var tlsStrategy = ClientTlsStrategyBuilder.create()
                .setSslContext(driver.sslContext())
                .build();
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
}
