// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.test;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.yahoo.jdisc.application.BindingRepository;
import com.yahoo.jdisc.application.ContainerActivator;
import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.http.filter.RequestFilter;
import com.yahoo.jdisc.http.filter.ResponseFilter;
import com.yahoo.jdisc.http.server.jetty.JettyHttpServer;
import com.yahoo.jdisc.http.ssl.SslKeyStore;
import com.yahoo.jdisc.test.TestDriver;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen Hult</a>
 */
public class ServerTestDriver {

    private final TestDriver driver;
    private final JettyHttpServer server;
    private final RemoteClient client;

    private ServerTestDriver(TestDriver driver, JettyHttpServer server, RemoteClient client) {
        this.driver = driver;
        this.server = server;
        this.client = client;
    }

    public boolean close() throws IOException {
        client.close();
        server.close();
        server.release();
        return driver.close();
    }

    public TestDriver parent() {
        return driver;
    }

    public ContainerActivator containerActivator() {
        return driver;
    }

    public JettyHttpServer server() {
        return server;
    }

    public RemoteClient client() {
        return client;
    }

    public HttpRequest newRequest(HttpRequest.Method method, String uri, HttpRequest.Version version) {
        return HttpRequest.newServerRequest(driver, newRequestUri(uri), method, version);
    }

    public URI newRequestUri(String uri) {
        return newRequestUri(URI.create(uri));
    }

    public URI newRequestUri(URI uri) {
        try {
            return new URI("http", null, "locahost",
                           server.getListenPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static ServerTestDriver newInstance(RequestHandler requestHandler, Module... guiceModules) throws IOException {
        return newInstance(requestHandler, Arrays.asList(guiceModules));
    }

    public static ServerTestDriver newInstance(RequestHandler requestHandler, Iterable<Module> guiceModules)
            throws IOException {
        List<Module> lst = new LinkedList<>();
        lst.add(newDefaultModule());
        for (Module module : guiceModules) {
            lst.add(module);
        }
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi(lst.toArray(new Module[lst.size()]));
        ContainerBuilder builder = driver.newContainerBuilder();
        builder.serverBindings().bind("*://*/*", requestHandler);
        JettyHttpServer server = builder.guiceModules().getInstance(JettyHttpServer.class);
        return newInstance(null, driver, builder, server);
    }

    private static ServerTestDriver newInstance(SslKeyStore clientTrustStore, TestDriver driver, ContainerBuilder builder,
                                                JettyHttpServer server) throws IOException {
        builder.serverProviders().install(server);
        driver.activateContainer(builder);
        try {
            server.start();
        } catch (RuntimeException e) {
            server.release();
            driver.close();
            throw e;
        }
        RemoteClient client;
        if (clientTrustStore == null) {
            client = RemoteClient.newInstance(server);
        } else {
            client = RemoteClient.newSslInstance(server, clientTrustStore);
        }
        return new ServerTestDriver(driver, server, client);
    }

    public static Module newDefaultModule() {
        return new AbstractModule() {

            @Override
            protected void configure() {
                bind(new TypeLiteral<BindingRepository<RequestFilter>>() { })
                        .toInstance(new BindingRepository<>());
                bind(new TypeLiteral<BindingRepository<ResponseFilter>>() { })
                        .toInstance(new BindingRepository<>());
            }
        };
    }

    public static Module newFilterModule(final BindingRepository<RequestFilter> requestFilters,
                                         final BindingRepository<ResponseFilter> responseFilters) {
        return new AbstractModule() {

            @Override
            protected void configure() {
                if (requestFilters != null) {
                    bind(new TypeLiteral<BindingRepository<RequestFilter>>() { }).toInstance(requestFilters);
                }
                if (responseFilters != null) {
                    bind(new TypeLiteral<BindingRepository<ResponseFilter>>() { }).toInstance(responseFilters);
                }
            }
        };
    }
}
