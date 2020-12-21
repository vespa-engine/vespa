// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.core.config.testutil;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Scopes;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.container.Container;
import com.yahoo.container.core.config.HandlersConfigurerDi;
import com.yahoo.container.di.CloudSubscriberFactory;
import com.yahoo.container.di.ComponentDeconstructor;
import com.yahoo.container.handler.threadpool.ContainerThreadPool;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.language.Linguistics;
import com.yahoo.language.simple.SimpleLinguistics;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Class for testing HandlersConfigurer.
 * Not for public use.
 *
 * If possible, please avoid using this class and HandlersConfigurer in your tests
 * @author Tony Vaagenes
 * @author gjoranv
 *
*/
public class HandlersConfigurerTestWrapper {

    private final ConfigSourceSet configSources =
            new ConfigSourceSet(this.getClass().getSimpleName() + ": " + new Random().nextLong());
    private final HandlersConfigurerDi configurer;

    // TODO: Remove once tests use ConfigSet rather than dir:
    private final static String[] testFiles = {
            "components.cfg",
            "handlers.cfg",
            "platform-bundles.cfg",
            "application-bundles.cfg",
            "string.cfg",
            "int.cfg",
            "renderers.cfg",
            "diagnostics.cfg",
            "qr-templates.cfg",
            "documentmanager.cfg",
            "schemamapping.cfg",
            "chains.cfg",
            "container-mbus.cfg",
            "container-mbus.cfg",
            "specialtokens.cfg",
            "documentdb-info.cfg",
            "qr-search.cfg",
            "query-profiles.cfg"
    };
    private final Set<File> createdFiles = new LinkedHashSet<>();
    private int lastGeneration = 0;
    private final Container container;

    private void createFiles(String configId) {
        if (configId.startsWith("dir:")) {
            try {
                System.setProperty("config.id", configId);
                String dirName = configId.substring(4);
                for (String file : testFiles) {
                    createIfNotExists(dirName, file);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // TODO: Remove once tests use ConfigSet rather than dir:
    private void createIfNotExists(String dir, String file) throws IOException {
        final File f = new File(dir + "/" + file);
        if (f.createNewFile()) {
            createdFiles.add(f);
        }
    }

    public HandlersConfigurerTestWrapper(String configId) {
        this(Container.get(), configId);
    }

    public HandlersConfigurerTestWrapper(Container container, String configId) {
        createFiles(configId);
        MockOsgiWrapper mockOsgiWrapper = new MockOsgiWrapper();
        ComponentDeconstructor testDeconstructor = getTestDeconstructor();
        configurer = new HandlersConfigurerDi(
                new CloudSubscriberFactory(configSources),
                container,
                configId,
                testDeconstructor,
                guiceInjector(),
                mockOsgiWrapper);
        this.container = container;
    }

    private ComponentDeconstructor getTestDeconstructor() {
        return (components, bundles) -> components.forEach(component -> {
            if (component instanceof AbstractComponent) {
                AbstractComponent abstractComponent = (AbstractComponent) component;
                if (abstractComponent.isDeconstructable()) abstractComponent.deconstruct();
            }
            if (! bundles.isEmpty()) throw new IllegalArgumentException("This test should not use bundles");
        });
    }

    public void reloadConfig() {
        configurer.reloadConfig(++lastGeneration);
        configurer.getNewComponentGraph(guiceInjector(), false);
    }

    public void shutdown() {
        configurer.shutdown(getTestDeconstructor());
        // TODO: Remove once tests use ConfigSet rather than dir:
        for (File f : createdFiles) {
            f.delete();
        }
    }

    public ComponentRegistry<RequestHandler> getRequestHandlerRegistry() {
        return container.getRequestHandlerRegistry();
    }

    private static Injector guiceInjector() {
        return Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                // Needed by e.g. SearchHandler
                bind(Linguistics.class).to(SimpleLinguistics.class).in(Scopes.SINGLETON);
                bind(ContainerThreadPool.class).to(SimpleContainerThreadpool.class);
            }
        });
    }

    private static class SimpleContainerThreadpool implements ContainerThreadPool {

        private final Executor executor = Executors.newCachedThreadPool();

        @Override public Executor executor() { return executor; }

    }

}
