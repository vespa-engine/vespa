// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.test;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.ning.http.util.AllowAllHostnameVerifier;
import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.jdisc.http.client.HttpClient;
import com.yahoo.jdisc.http.client.HttpClientConfig;
import com.yahoo.jdisc.http.client.filter.ResponseFilter;
import com.yahoo.jdisc.service.CurrentContainer;
import com.yahoo.jdisc.test.TestDriver;

import javax.net.ssl.HostnameVerifier;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class ClientTestDriver {

    private final TestDriver driver;
    private final HttpClient client;
    private final RemoteServer server;

    private ClientTestDriver(TestDriver driver, HttpClient client) throws IOException {
        this.driver = driver;
        this.client = client;
        this.server = RemoteServer.newInstance();
    }

    public CurrentContainer currentContainer() {
        return driver;
    }

    public boolean close() {
        if (!server.close(60, TimeUnit.SECONDS)) {
            return false;
        }
        client.release();
        return driver.close();
    }

    public HttpClient client() {
        return client;
    }

    public RemoteServer server() {
        return server;
    }

    public static ClientTestDriver newInstance(Module... guiceModules) throws IOException {
        return newInstance(new HttpClientConfig.Builder().sslConnectionPoolEnabled(false),
                           guiceModules);
    }

    public static ClientTestDriver newInstance(HttpClientConfig.Builder config, Module... guiceModules)
            throws IOException {
        Module[] lst = new Module[guiceModules.length + 2];
        lst[0] = newDefaultModule();
        lst[lst.length - 1] = newConfigModule(config);
        System.arraycopy(guiceModules, 0, lst, 1, guiceModules.length);
        return newInstanceImpl(HttpClient.class, lst);
    }

    private static ClientTestDriver newInstanceImpl(Class<? extends HttpClient> clientClass,
                                                    Module... guiceModules) throws IOException {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi(guiceModules);
        ContainerBuilder builder = driver.newContainerBuilder();
        HttpClient client = builder.guiceModules().getInstance(clientClass);
        builder.serverBindings().bind("*://*/*", client);
        driver.activateContainer(builder);
        try {
            client.start();
        } catch (RuntimeException e) {
            client.release();
            driver.close();
            throw e;
        }
        return new ClientTestDriver(driver, client);
    }

    public static Module newDefaultModule() {
        return new AbstractModule() {

            @Override
            protected void configure() {
                bind(HostnameVerifier.class).to(AllowAllHostnameVerifier.class);
                bind(new TypeLiteral<List<ResponseFilter>>() { }).toInstance(Collections.<ResponseFilter>emptyList());
            }
        };
    }

    public static Module newConfigModule(final HttpClientConfig.Builder config) {
        return new AbstractModule() {

            @Override
            protected void configure() {
                bind(HttpClientConfig.class).toInstance(new HttpClientConfig(config));
            }
        };
    }

    public static Module newFilterModule(final ResponseFilter... filters) {
        return new AbstractModule() {

            @Override
            protected void configure() {
                bind(new TypeLiteral<List<ResponseFilter>>() { }).toInstance(Arrays.asList(filters));
            }
        };
    }
}
