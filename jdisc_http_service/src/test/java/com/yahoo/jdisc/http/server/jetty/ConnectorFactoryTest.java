// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.ServerConfig;
import com.yahoo.jdisc.http.ssl.impl.ConfiguredSslContextFactoryProvider;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;

/**
 * @author Einar M R Rosenvinge
 */
public class ConnectorFactoryTest {

    @Test
    public void requireThatServerCanBindChannel() throws Exception {
        Server server = new Server();
        try {
            ConnectorConfig config = new ConnectorConfig(new ConnectorConfig.Builder());
            ConnectorFactory factory = createConnectorFactory(config);
            JettyConnectionLogger connectionLogger = new JettyConnectionLogger(
                    new ServerConfig.ConnectionLog.Builder().enabled(false).build(),
                    new VoidConnectionLog());
            JDiscServerConnector connector =
                    (JDiscServerConnector)factory.createConnector(new DummyMetric(), server, connectionLogger);
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
        return new ConnectorFactory(config, new ConfiguredSslContextFactoryProvider(config));
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
