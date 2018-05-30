// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.core.config.testutil;

import com.google.inject.Guice;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.container.Container;
import com.yahoo.container.di.CloudSubscriberFactory;
import com.yahoo.container.di.ComponentDeconstructor;
import com.yahoo.container.core.config.HandlersConfigurerDi;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.osgi.MockOsgi;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;

/**
 * Class for testing HandlersConfigurer.
 * Not for public use.
 *
 * If possible, please avoid using this class and HandlersConfigurer in your tests
 * @author tonytv
 * @author gjoranv
 *
*/
public class HandlersConfigurerTestWrapper {
    private ConfigSourceSet configSources =
            new ConfigSourceSet(this.getClass().getSimpleName() + ": " + new Random().nextLong());
    private HandlersConfigurerDi configurer;

    // TODO: Remove once tests use ConfigSet rather than dir:
    private final static String testFiles[] = {
            "components.cfg",
            "handlers.cfg",
            "bundles.cfg",
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
        MockOsgi mockOsgi = new MockOsgi();
        container.setOsgi(mockOsgi);
        ComponentDeconstructor testDeconstructor = getTestDeconstructor();
        configurer = new HandlersConfigurerDi(
                new CloudSubscriberFactory(configSources),
                container,
                configId,
                testDeconstructor,
                Guice.createInjector(),
                mockOsgi);
        this.container = container;
    }

    private ComponentDeconstructor getTestDeconstructor() {
        return new ComponentDeconstructor() {
            @Override
            public void deconstruct(Object component) {
                if (component instanceof AbstractComponent) {
                    AbstractComponent abstractComponent = (AbstractComponent) component;
                    if (abstractComponent.isDeconstructable())
                        ((AbstractComponent) component).deconstruct();
            }
        }};
    }

    public void reloadConfig() {
        configurer.reloadConfig(++lastGeneration);
        try {
            configurer.getNewConfigGraph(Guice.createInjector(), false);
        } catch (InterruptedException e) {
           throw new RuntimeException(e);
        }
    }

    public void shutdown() {
        // TODO: Remove once tests use ConfigSet rather than dir:
        for (File f : createdFiles) {
            f.delete();
        }
    }

    public ComponentRegistry<RequestHandler> getRequestHandlerRegistry() {
        return container.getRequestHandlerRegistry();
    }

}
