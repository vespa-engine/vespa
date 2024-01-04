// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.container.logging.ConnectionLog;
import com.yahoo.container.logging.ConnectionLogEntry;
import com.yahoo.container.logging.RequestLog;
import com.yahoo.container.logging.RequestLogEntry;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.ServerConfig;
import com.yahoo.security.SslContextBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.http.HttpClientTransportOverHTTP;
import org.eclipse.jetty.io.ClientConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.jdisc.http.server.jetty.Utils.generatePrivateKeyAndCertificate;
import static com.yahoo.yolean.Exceptions.uncheckInterrupted;
import static org.eclipse.jetty.client.ProxyProtocolClientConnectionFactory.V1;
import static org.eclipse.jetty.client.ProxyProtocolClientConnectionFactory.V2;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author bjorncs
 */
@Timeout(value = 60)
class ProxyProtocolTest {

    private static final Logger log = Logger.getLogger(ProxyProtocolTest.class.getName());

    private static Path privateKeyFile;
    private static Path certificateFile;
    private InMemoryConnectionLog connectionLog;
    private InMemoryRequestLog requestLogMock;

    @BeforeAll
    static void generateCrypto(@TempDir Path tmpFolder) throws IOException {
        privateKeyFile = tmpFolder.resolve("key.pem");
        certificateFile = tmpFolder.resolve("cert.pem");
        generatePrivateKeyAndCertificate(privateKeyFile, certificateFile);
    }

    @BeforeEach
    void initializeServer() {
        requestLogMock = new InMemoryRequestLog();
        connectionLog = new InMemoryConnectionLog();
    }

    @Test
    void requireThatProxyProtocolIsAcceptedAndActualRemoteAddressStoredInAccessLog() throws Exception {
        String proxiedRemoteAddress = "192.168.0.100";
        int proxiedRemotePort = 12345;
        JettyTestDriver driver = createSslWithProxyProtocolTestDriver(certificateFile, privateKeyFile, requestLogMock, connectionLog, false);
        sendJettyClientRequest(driver, certificateFile, new V1.Tag(proxiedRemoteAddress, proxiedRemotePort));
        sendJettyClientRequest(driver, certificateFile, new V2.Tag(proxiedRemoteAddress, proxiedRemotePort));

        assertLogSizeAndCloseDriver(driver, requestLogMock, 2, connectionLog, 2);

        assertLogEntryHasRemote(requestLogMock.entries().get(0), proxiedRemoteAddress, proxiedRemotePort);
        assertLogEntryHasRemote(requestLogMock.entries().get(1), proxiedRemoteAddress, proxiedRemotePort);
        assertLogEntryHasRemote(connectionLog.logEntries().get(0), proxiedRemoteAddress, proxiedRemotePort);
        assertEquals("v1", connectionLog.logEntries().get(0).proxyProtocolVersion().get());
        assertLogEntryHasRemote(connectionLog.logEntries().get(1), proxiedRemoteAddress, proxiedRemotePort);
        assertEquals("v2", connectionLog.logEntries().get(1).proxyProtocolVersion().get());
    }

    @Test
    void requireThatConnectorWithProxyProtocolMixedEnabledAcceptsBothProxyProtocolAndHttps() throws Exception {
        JettyTestDriver driver = createSslWithProxyProtocolTestDriver(certificateFile, privateKeyFile, requestLogMock, connectionLog, true);

        String proxiedRemoteAddress = "192.168.0.100";
        sendJettyClientRequest(driver, certificateFile, null);
        sendJettyClientRequest(driver, certificateFile, new V1.Tag(proxiedRemoteAddress, 12345));
        sendJettyClientRequest(driver, certificateFile, new V2.Tag(proxiedRemoteAddress, 12345));

        assertLogSizeAndCloseDriver(driver, requestLogMock, 3, connectionLog, 3);

        assertLogEntryHasRemote(requestLogMock.entries().get(0), "127.0.0.1", 0);
        assertLogEntryHasRemote(requestLogMock.entries().get(1), proxiedRemoteAddress, 0);
        assertLogEntryHasRemote(requestLogMock.entries().get(2), proxiedRemoteAddress, 0);
        assertLogEntryHasRemote(connectionLog.logEntries().get(0), null, 0);
        assertLogEntryHasRemote(connectionLog.logEntries().get(1), proxiedRemoteAddress, 12345);
        assertLogEntryHasRemote(connectionLog.logEntries().get(2), proxiedRemoteAddress, 12345);
    }

    @Test
    void requireThatJdiscLocalPortPropertyIsNotOverriddenByProxyProtocol() throws Exception {
        String proxiedRemoteAddress = "192.168.0.100";
        int proxiedRemotePort = 12345;
        String proxyLocalAddress = "10.0.0.10";
        int proxyLocalPort = 23456;
        JettyTestDriver driver = createSslWithProxyProtocolTestDriver(certificateFile, privateKeyFile, requestLogMock, connectionLog, false);
        V2.Tag v2Tag = new V2.Tag(
                V2.Tag.Command.PROXY, null, V2.Tag.Protocol.STREAM, proxiedRemoteAddress,
                proxiedRemotePort, proxyLocalAddress, proxyLocalPort, null);
        ContentResponse response = sendJettyClientRequest(driver, certificateFile, v2Tag);

        int clientPort = Integer.parseInt(response.getHeaders().get("Jdisc-Local-Port"));
        assertNotEquals(proxyLocalPort, clientPort);
        assertLogSizeAndCloseDriver(driver, requestLogMock, 1, connectionLog, 1);
        assertNotEquals(proxyLocalPort, connectionLog.logEntries().get(0).localPort().get().intValue());
    }

    @Test
    void requireThatSslConnectionFailsWhenMixedModeIsDisabled() throws Exception {
        JettyTestDriver driver = createSslWithProxyProtocolTestDriver(
                certificateFile, privateKeyFile, requestLogMock, connectionLog, false);
        try {
            sendJettyClientRequest(driver, certificateFile, null);
            fail("Expected exception");
        } catch (ExecutionException e) {
            assertInstanceOf(IOException.class, e.getCause());
        } finally {
            assertTrue(driver.close());
        }
    }

    private static JettyTestDriver createSslWithProxyProtocolTestDriver(
            Path certificateFile, Path privateKeyFile, RequestLog requestLog,
            ConnectionLog connectionLog, boolean mixedMode) {
        ConnectorConfig.Builder connectorConfig = new ConnectorConfig.Builder()
                .proxyProtocol(new ConnectorConfig.ProxyProtocol.Builder()
                        .enabled(true)
                        .mixedMode(mixedMode))
                .ssl(new ConnectorConfig.Ssl.Builder()
                        .enabled(true)
                        .privateKeyFile(privateKeyFile.toString())
                        .certificateFile(certificateFile.toString())
                        .caCertificateFile(certificateFile.toString()));
        return JettyTestDriver.newConfiguredInstance(
                new EchoRequestHandler(),
                new ServerConfig.Builder().connectionLog(new ServerConfig.ConnectionLog.Builder().enabled(true)),
                connectorConfig,
                binder -> {
                    binder.bind(RequestLog.class).toInstance(requestLog);
                    binder.bind(ConnectionLog.class).toInstance(connectionLog);
                });
    }

    private ContentResponse sendJettyClientRequest(JettyTestDriver testDriver, Path certificateFile, Object tag)
            throws Exception {
        ExecutionException cause = null;
        int maxAttempts = 10;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            HttpClient client = createJettyHttpClient(certificateFile);
            try {
                ContentResponse response = client.newRequest(URI.create("https://localhost:" + testDriver.server().getListenPort() + "/"))
                        .tag(tag)
                        .send();
                assertEquals(200, response.getStatus());
                return response;
            } catch (ExecutionException e) {
                // Retry when the server closes the connection before the TLS handshake is completed. This has been observed in CI.
                // We have been unable to reproduce this locally. The cause is therefor currently unknown.
                log.log(Level.WARNING, String.format("Attempt %d failed: %s", attempt, e.getMessage()), e);
                Thread.sleep(10);
                cause = e;
            } finally {
                client.stop();
                client.destroy();
            }
        }
        throw cause;
    }

    // Using Jetty's http client as Apache httpclient does not support the proxy-protocol v1/v2.
    private static HttpClient createJettyHttpClient(Path certificateFile) throws Exception {
        var ssl = new SslContextFactory.Client();
        ssl.setHostnameVerifier(NoopHostnameVerifier.INSTANCE);
        ssl.setSslContext(new SslContextBuilder().withTrustStore(certificateFile).build());
        var connector = new ClientConnector();
        connector.setSslContextFactory(ssl);
        HttpClient client = new HttpClient(new HttpClientTransportOverHTTP(connector));
        int timeout = 20 * 1000;
        client.setConnectTimeout(timeout);
        client.setIdleTimeout(timeout);
        client.start();
        return client;
    }

    private static void assertLogEntryHasRemote(RequestLogEntry entry, String expectedAddress, int expectedPort) {
        assertEquals(expectedAddress, entry.peerAddress().get());
        if (expectedPort > 0) {
            assertEquals(expectedPort, entry.peerPort().getAsInt());
        }
    }

    private static void assertLogEntryHasRemote(ConnectionLogEntry entry, String expectedAddress, int expectedPort) {
        if (expectedAddress != null) {
            assertEquals(expectedAddress, entry.remoteAddress().get());
        } else {
            assertTrue(entry.remoteAddress().isEmpty());
        }
        if (expectedPort > 0) {
            assertEquals(expectedPort, entry.remotePort().get());
        } else {
            assertTrue(entry.remotePort().isEmpty());
        }
    }

    /* Don't close Jetty to early ensuring that the request log is written */
    private static void assertLogSizeAndCloseDriver(
            JettyTestDriver driver, InMemoryRequestLog reqLog, int expectedReqLogSize, InMemoryConnectionLog connLog,
            int expectedConnLogSize) {
        Predicate<Void> waitCondition = __ ->
                reqLog.entries().size() < expectedReqLogSize && connLog.logEntries().size() < expectedConnLogSize;
        await(waitCondition);
        assertTrue(driver.close());
        if (waitCondition.test(null)) await(waitCondition);
        assertEquals(expectedReqLogSize, reqLog.entries().size());
        assertEquals(expectedConnLogSize, connLog.logEntries().size());
    }

    private static void await(Predicate<Void> waitCondition) {
        for (int attempt = 0; attempt < 1000 && waitCondition.test(null); attempt++) {
            uncheckInterrupted(() -> Thread.sleep(10));
        }
    }
}
