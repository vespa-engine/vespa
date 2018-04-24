// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.ssl.DefaultSslKeyStoreConfigurator;
import com.yahoo.jdisc.http.ssl.DefaultSslTrustStoreConfigurator;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.testng.annotations.Test;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.util.Map;

import static com.yahoo.jdisc.http.ConnectorConfig.Ssl;
import static com.yahoo.jdisc.http.ConnectorConfig.Ssl.KeyStoreType.Enum.JKS;
import static com.yahoo.jdisc.http.ConnectorConfig.Ssl.KeyStoreType.Enum.PEM;
import static org.hamcrest.CoreMatchers.equalTo;

/**
 * @author Einar M R Rosenvinge
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
        ConnectorFactory willThrowException = createConnectorFactory(config);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void ssl_pem_config_is_validated() {
        ConnectorConfig config = new ConnectorConfig(
                new ConnectorConfig.Builder()
                        .ssl(new Ssl.Builder()
                                     .enabled(true)
                                     .keyStoreType(PEM)
                                     .keyStorePath("nonEmpty")));
        ConnectorFactory willThrowException = createConnectorFactory(config);
    }

    @Test
    public void requireThatNoPreBoundChannelWorks() throws Exception {
        Server server = new Server();
        try {
            ConnectorConfig config = new ConnectorConfig(new ConnectorConfig.Builder());
            ConnectorFactory factory = createConnectorFactory(config);
            JDiscServerConnector connector =
                    (JDiscServerConnector)factory.createConnector(new DummyMetric(), server, null);
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

            ConnectorConfig config = new ConnectorConfig(new ConnectorConfig.Builder());
            ConnectorFactory factory = createConnectorFactory(config);
            JDiscServerConnector connector =
                    (JDiscServerConnector) factory.createConnector(new DummyMetric(), server, serverChannel);
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

    private static ConnectorFactory createConnectorFactory(ConnectorConfig config) {
        ThrowingSecretStore secretStore = new ThrowingSecretStore();
        return new ConnectorFactory(config,
                                    new DefaultSslKeyStoreConfigurator(config, secretStore),
                                    new DefaultSslTrustStoreConfigurator(config, secretStore));
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

    @SuppressWarnings("deprecation")
    private static final class ThrowingSecretStore implements com.yahoo.jdisc.http.SecretStore {

        @Override
        public String getSecret(String key) {
            throw new UnsupportedOperationException("A secret store is not available");
        }

    }

}
