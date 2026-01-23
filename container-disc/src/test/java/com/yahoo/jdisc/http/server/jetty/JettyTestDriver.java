// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.google.inject.Module;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.ServerConfig;
import com.yahoo.jdisc.http.server.jetty.testutils.TestDriver;

import javax.net.ssl.SSLContext;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * Provides functionality for setting up a jdisc container with an HTTP server, handlers and a simple http client.
 *
 * @author bjorncs
 * @author Simon Thoresen Hult
 * @author bakksjo
 */
public class JettyTestDriver {

    public enum TlsClientAuth { NEED, WANT }

    private final TestDriver driver;
    private final SimpleHttpClient client;

    private JettyTestDriver(RequestHandler requestHandler,
                            ServerConfig serverConfig,
                            ConnectorConfig connectorConfig,
                            Collection<Module> guiceModules) {
        var builder = TestDriver.newBuilder()
                .withRequestHandler(requestHandler)
                .withServerConfig(serverConfig)
                .withConnectorConfig(connectorConfig);
        guiceModules.forEach(builder::withGuiceModule);
        this.driver = builder.build();
        this.client = new SimpleHttpClient(driver.sslContext(), driver.server().getListenPort(), false);
    }

    public boolean close() {
        uncheck(client::close);
        return driver.shutdown();
    }

    public JettyHttpServer server() { return driver.server(); }
    public SimpleHttpClient client() { return client; }
    public SSLContext sslContext() { return driver.sslContext(); }

    public SimpleHttpClient newClient(boolean useCompression) {
        return new SimpleHttpClient(driver.sslContext(), driver.server().getListenPort(), useCompression);
    }

    public static JettyTestDriver newConfiguredInstance(RequestHandler requestHandler,
                                                        ServerConfig.Builder serverConfig,
                                                        ConnectorConfig.Builder connectorConfig,
                                                        Module... guiceModules) {
        return new JettyTestDriver(requestHandler, serverConfig.build(), connectorConfig.build(), List.of(guiceModules));
    }

    public static JettyTestDriver newInstance(RequestHandler requestHandler, Module... guiceModules) {
        return newConfiguredInstance(requestHandler, new ServerConfig.Builder(), new ConnectorConfig.Builder(), guiceModules);
    }


    public static JettyTestDriver newInstanceWithSsl(RequestHandler requestHandler,
                                                     Path certificateFile,
                                                     Path privateKeyFile,
                                                     TlsClientAuth tlsClientAuth,
                                                     Module... guiceModules) {
        return newConfiguredInstance(
                requestHandler,
                new ServerConfig.Builder().connectionLog(new ServerConfig.ConnectionLog.Builder().enabled(true)),
                new ConnectorConfig.Builder()
                        .ssl(new ConnectorConfig.Ssl.Builder()
                                .enabled(true)
                                .clientAuth(tlsClientAuth == TlsClientAuth.NEED
                                        ? ConnectorConfig.Ssl.ClientAuth.Enum.NEED_AUTH
                                        : ConnectorConfig.Ssl.ClientAuth.Enum.WANT_AUTH)
                                .privateKeyFile(privateKeyFile.toString())
                                .certificateFile(certificateFile.toString())
                                .caCertificateFile(certificateFile.toString())),
                guiceModules);
    }

}
