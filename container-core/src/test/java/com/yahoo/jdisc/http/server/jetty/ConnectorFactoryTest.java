// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.ServerConfig;
import com.yahoo.jdisc.http.ssl.impl.ConfiguredSslContextFactoryProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Einar M R Rosenvinge
 * @author bjorncs
 */
public class ConnectorFactoryTest {

    private Server server;

    @BeforeEach
    public void createServer() {
        server = new Server();
    }

    @AfterEach
    public void stopServer() {
        try {
            server.stop();
            server = null;
        } catch (Exception e) {
            //ignore
        }
    }

    @Test
    void requireThatServerCanBindChannel() throws Exception {
        ConnectorConfig config = new ConnectorConfig(new ConnectorConfig.Builder());
        ConnectorFactory factory = createConnectorFactory(config);
        JDiscServerConnector connector = createConnectorFromFactory(factory);
        server.addConnector(connector);
        server.setHandler(new HelloWorldHandler());
        server.start();

        SimpleHttpClient client = new SimpleHttpClient(null, connector.getLocalPort(), false);
        SimpleHttpClient.RequestExecutor ex = client.newGet("/blaasdfnb");
        SimpleHttpClient.ResponseValidator val = ex.execute();
        val.expectContent(equalTo("Hello world"));
    }

    @Test
    void constructed_connector_is_based_on_jdisc_connector_config() {
        ConnectorConfig config = new ConnectorConfig.Builder()
                .idleTimeout(25)
                .name("my-server-name")
                .listenPort(12345)
                .build();
        ConnectorFactory factory = createConnectorFactory(config);
        JDiscServerConnector connector = createConnectorFromFactory(factory);
        assertEquals(25000, connector.getIdleTimeout());
        assertEquals(12345, connector.listenPort());
        assertEquals("my-server-name", connector.getName());
    }

    private static ConnectorFactory createConnectorFactory(ConnectorConfig config) {
        return new ConnectorFactory(config, new ConfiguredSslContextFactoryProvider(config));
    }

    private JDiscServerConnector createConnectorFromFactory(ConnectorFactory factory) {
        JettyConnectionLogger connectionLogger = new JettyConnectionLogger(
                new ServerConfig.ConnectionLog.Builder().enabled(false).build(),
                new VoidConnectionLog());
        DummyMetric metric = new DummyMetric();
        var connectionMetricAggregator = new ConnectionMetricAggregator(new ServerConfig(new ServerConfig.Builder()), metric);
        return (JDiscServerConnector)factory.createConnector(metric, server, connectionLogger, connectionMetricAggregator);
    }

    private static class HelloWorldHandler extends AbstractHandler {
        @Override
        public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
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

}
