// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.container.logging.ConnectionLog;
import com.yahoo.container.logging.ConnectionLogEntry;
import com.yahoo.container.logging.RequestLog;
import com.yahoo.container.logging.RequestLogEntry;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.ServerConfig;
import com.yahoo.security.SslContextBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.assertj.core.api.Assertions;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
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
        assertTrue(driver.close());

        assertLogSize(2, requestLogMock.entries());
        assertLogEntryHasRemote(requestLogMock.entries().get(0), proxiedRemoteAddress, proxiedRemotePort);
        assertLogEntryHasRemote(requestLogMock.entries().get(1), proxiedRemoteAddress, proxiedRemotePort);
        Assertions.assertThat(connectionLog.logEntries()).hasSize(2);
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
        assertTrue(driver.close());

        assertLogSize(3, requestLogMock.entries());
        assertLogEntryHasRemote(requestLogMock.entries().get(0), "127.0.0.1", 0);
        assertLogEntryHasRemote(requestLogMock.entries().get(1), proxiedRemoteAddress, 0);
        assertLogEntryHasRemote(requestLogMock.entries().get(2), proxiedRemoteAddress, 0);
        Assertions.assertThat(connectionLog.logEntries()).hasSize(3);
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
        assertTrue(driver.close());

        int clientPort = Integer.parseInt(response.getHeaders().get("Jdisc-Local-Port"));
        assertNotEquals(proxyLocalPort, clientPort);
        assertLogSize(1, connectionLog.logEntries());
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
        HttpClient client = createJettyHttpClient(certificateFile);
        ExecutionException cause = null;
        try {
            int maxAttempts = 3;
            for (int attempt = 0; attempt < maxAttempts; attempt++) {
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
                }
            }
            throw cause;
        } finally {
            client.stop();
            client.destroy();
        }
    }

    // Using Jetty's http client as Apache httpclient does not support the proxy-protocol v1/v2.
    private static HttpClient createJettyHttpClient(Path certificateFile) throws Exception {
        SslContextFactory.Client clientSslCtxFactory = new SslContextFactory.Client();
        clientSslCtxFactory.setHostnameVerifier(NoopHostnameVerifier.INSTANCE);
        clientSslCtxFactory.setSslContext(new SslContextBuilder().withTrustStore(certificateFile).build());

        HttpClient client = new HttpClient(clientSslCtxFactory);
        client.setConnectTimeout(60*1000);
        client.setStopTimeout(60*1000);
        client.setIdleTimeout(60*1000);
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
            Assertions.assertThat(entry.remoteAddress()).hasValue(expectedAddress);
        } else {
            Assertions.assertThat(entry.remoteAddress()).isEmpty();
        }
        if (expectedPort > 0) {
            Assertions.assertThat(entry.remotePort()).hasValue(expectedPort);
        } else {
            Assertions.assertThat(entry.remotePort()).isEmpty();
        }
    }


    private static void assertLogSize(int expectedItems, Collection<?> items) {
        for (int attempt = 0; attempt < 10; attempt++) {
            if (items.size() >= expectedItems) break;
            uncheckInterrupted(() -> Thread.sleep(200));
        }
        Assertions.assertThat(items).hasSize(expectedItems);
    }
}
