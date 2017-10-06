// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.google.common.collect.ImmutableMap;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.SecretStore;
import com.yahoo.jdisc.http.ssl.ReaderForPath;
import com.yahoo.jdisc.http.ssl.SslContextFactory;
import com.yahoo.jdisc.http.ssl.SslKeyStore;
import com.yahoo.jdisc.http.ssl.pem.PemKeyStore;
import com.yahoo.jdisc.http.ssl.pem.PemSslKeyStore;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.testng.annotations.Test;

import javax.net.ssl.SSLContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.FileChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Map;

import static com.yahoo.jdisc.http.ConnectorConfig.Ssl;
import static com.yahoo.jdisc.http.ConnectorConfig.Ssl.KeyStoreType.Enum.JKS;
import static com.yahoo.jdisc.http.ConnectorConfig.Ssl.KeyStoreType.Enum.PEM;
import static org.hamcrest.CoreMatchers.equalTo;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class ConnectorFactoryTest {

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void ssl_jks_config_is_validated() {
        ConnectorConfig config = new ConnectorConfig(
                new ConnectorConfig.Builder()
                        .ssl(new Ssl.Builder()
                                     .enabled(true)
                                     .keyStoreType(JKS)
                                     .pemKeyStore(
                                             new Ssl.PemKeyStore.Builder()
                                                     .keyPath("nonEmpty"))));

        ConnectorFactory willThrowException = new ConnectorFactory(config, new ThrowingSecretStore());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void ssl_pem_config_is_validated() {
        ConnectorConfig config = new ConnectorConfig(
                new ConnectorConfig.Builder()
                        .ssl(new Ssl.Builder()
                                     .enabled(true)
                                     .keyStoreType(PEM)
                                     .keyStorePath("nonEmpty")));

        ConnectorFactory willThrowException = new ConnectorFactory(config, new ThrowingSecretStore());
    }

    @Test
    public void requireThatNoPreBoundChannelWorks() throws Exception {
        Server server = new Server();
        try {
            ConnectorFactory factory = new ConnectorFactory(new ConnectorConfig(new ConnectorConfig.Builder()),
                                                            new ThrowingSecretStore());
            ConnectorFactory.JDiscServerConnector connector =
                    (ConnectorFactory.JDiscServerConnector)factory.createConnector(new DummyMetric(), server, null, Collections.emptyMap());
            server.addConnector(connector);
            server.setHandler(new HelloWorldHandler());
            server.start();

            SimpleHttpClient client = new SimpleHttpClient(null, connector.getLocalPort(), false);
            SimpleHttpClient.RequestExecutor ex = client.newGet("/blaasdfnb");
            SimpleHttpClient.ResponseValidator val = ex.execute();
            val.expectContent(equalTo("Hello world"));
        } finally {
            try {
                server.stop();
            } catch (Exception e) {
                //ignore
            }
        }
    }

    @Test
    public void requireThatPreBoundChannelWorks() throws Exception {
        Server server = new Server();
        try {
            ServerSocketChannel serverChannel = ServerSocketChannel.open();
            serverChannel.socket().bind(new InetSocketAddress(0));

            ConnectorFactory factory = new ConnectorFactory(new ConnectorConfig(new ConnectorConfig.Builder()), new ThrowingSecretStore());
            ConnectorFactory.JDiscServerConnector connector = (ConnectorFactory.JDiscServerConnector) factory.createConnector(new DummyMetric(), server, serverChannel, Collections.emptyMap());
            server.addConnector(connector);
            server.setHandler(new HelloWorldHandler());
            server.start();

            SimpleHttpClient client = new SimpleHttpClient(null, connector.getLocalPort(), false);
            SimpleHttpClient.RequestExecutor ex = client.newGet("/blaasdfnb");
            SimpleHttpClient.ResponseValidator val = ex.execute();
            val.expectContent(equalTo("Hello world"));
        } finally {
            try {
                server.stop();
            } catch (Exception e) {
                //ignore
            }
        }
    }

    @Test
    public void pre_bound_keystore_file_channels_are_used() throws Exception {
        Path pemKeyStoreDirectory = Paths.get("src/test/resources/pem/");

        Path certificateFile = pemKeyStoreDirectory.resolve("test.crt");
        Path privateKeyFile = pemKeyStoreDirectory.resolve("test.key");

        Server server = new Server();
        try {
            ServerSocketChannel serverChannel = ServerSocketChannel.open();
            serverChannel.socket().bind(new InetSocketAddress(0));

            String fakeCertificatePath = "ensure-certificate-path-is-not-used-to-open-the-file";
            String fakeKeyPath = "ensure-key-path-is-not-used-to-open-the-file";

            ConnectorConfig.Builder builder = new ConnectorConfig.Builder();
            builder.ssl(
                    new Ssl.Builder().
                            enabled(true).
                            keyStoreType(PEM).
                            pemKeyStore(new Ssl.PemKeyStore.Builder().
                                    certificatePath(fakeCertificatePath).
                                    keyPath(fakeKeyPath)));

            FileChannel certificateChannel = FileChannel.open(certificateFile, StandardOpenOption.READ);
            FileChannel privateKeyChannel = FileChannel.open(privateKeyFile, StandardOpenOption.READ);

            Map<Path, FileChannel> keyStoreChannels = ImmutableMap.<Path, FileChannel>builder().
                    put(Paths.get(fakeCertificatePath), certificateChannel).
                    put(Paths.get(fakeKeyPath), privateKeyChannel).
                    build();


            ConnectorFactory factory = new ConnectorFactory(new ConnectorConfig(builder), new ThrowingSecretStore());
            ConnectorFactory.JDiscServerConnector connector = (ConnectorFactory.JDiscServerConnector) factory.createConnector(new DummyMetric(), server, serverChannel, keyStoreChannels);
            server.addConnector(connector);
            server.setHandler(new HelloWorldHandler());
            server.start();

            SslKeyStore trustStore = new PemSslKeyStore(
                    new PemKeyStore.TrustStoreLoadParameter(
                            new ReaderForPath(Files.newBufferedReader(certificateFile), certificateFile)));

            SSLContext clientSslContext = SslContextFactory.newInstanceFromTrustStore(trustStore).getServerSSLContext();
            SimpleHttpClient client = new SimpleHttpClient(clientSslContext, connector.getLocalPort(), false);
            SimpleHttpClient.RequestExecutor ex = client.newGet("/ignored");
            SimpleHttpClient.ResponseValidator val = ex.execute();
            val.expectContent(equalTo("Hello world"));
        } finally {
            try {
                server.stop();
            } catch (Exception e) {
                //ignore
            }
        }
    }

    private static class HelloWorldHandler extends AbstractHandler {
        @Override
        public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
            response.getWriter().write("Hello world");
            response.getWriter().flush();
            response.getWriter().close();
            baseRequest.setHandled(true);
        }
    }

    private static class DummyMetric implements Metric {
        @Override
        public void set(String key, Number val, Context ctx) { }

        @Override
        public void add(String key, Number val, Context ctx) { }

        @Override
        public Context createContext(Map<String, ?> properties) {
            return new DummyContext();
        }
    }

    private static class DummyContext implements Metric.Context {
    }

    private static final class ThrowingSecretStore implements SecretStore {

        @Override
        public String getSecret(String key) {
            throw new UnsupportedOperationException("A secret store is not available");
        }

    }

}
