// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.yahoo.jdisc.application.ContainerActivator;
import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.jdisc.application.ContainerThread;
import com.yahoo.jdisc.application.OsgiFramework;
import com.yahoo.jdisc.service.CurrentContainer;

import java.util.concurrent.ThreadFactory;

/**
 * @author Simon Thoresen
 */
class ApplicationEnvironmentModule extends AbstractModule {

    private final ApplicationLoader loader;

    public ApplicationEnvironmentModule(ApplicationLoader loader) {
        this.loader = loader;
    }

    @Override
    protected void configure() {
        bind(ContainerActivator.class).toInstance(loader);
        bind(CurrentContainer.class).toInstance(loader);
        bind(OsgiFramework.class).toInstance(loader.osgiFramework());
        bind(ThreadFactory.class).to(ContainerThread.Factory.class);
    }

    @Provides
    public ContainerBuilder containerBuilder() {
        return loader.newContainerBuilder();
    }
}
