// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.yahoo.jdisc.AbstractResource;
import com.yahoo.jdisc.SharedResource;
import com.yahoo.jdisc.application.BindingSet;
import com.yahoo.jdisc.application.BindingSetSelector;
import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.jdisc.application.ResourcePool;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.service.BindingSetNotFoundException;
import com.yahoo.jdisc.service.CurrentContainer;
import com.yahoo.jdisc.service.NoBindingSetSelectedException;
import com.yahoo.jdisc.service.ServerProvider;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Simon Thoresen
 */
public class ActiveContainer extends AbstractResource implements CurrentContainer {

    private final ContainerTermination termination;
    private final Injector guiceInjector;
    private final Iterable<ServerProvider> serverProviders;
    private final ResourcePool resourceReferences = new ResourcePool();
    private final Map<String, BindingSet<RequestHandler>> serverBindings;
    private final Map<String, BindingSet<RequestHandler>> clientBindings;
    private final BindingSetSelector bindingSetSelector;
    private final TimeoutManagerImpl timeoutMgr;
    final Destructor destructor;

    public ActiveContainer(ContainerBuilder builder) {
        serverProviders = builder.serverProviders().activate();
        serverProviders.forEach(resourceReferences::retain);
        serverBindings = builder.activateServerBindings();
        serverBindings.forEach(
                (ignoredName, bindingSet) -> bindingSet.forEach(
                        binding -> resourceReferences.retain(binding.getValue())));
        clientBindings = builder.activateClientBindings();
        clientBindings.forEach(
                (ignoredName, bindingSet) -> bindingSet.forEach(
                        binding -> resourceReferences.retain(binding.getValue())));
        bindingSetSelector = builder.getInstance(BindingSetSelector.class);
        timeoutMgr = builder.getInstance(TimeoutManagerImpl.class);
        timeoutMgr.start();
        builder.guiceModules().install(new AbstractModule() {

            @Override
            protected void configure() {
                bind(TimeoutManagerImpl.class).toInstance(timeoutMgr);
            }
        });
        guiceInjector = builder.guiceModules().activate();
        termination = new ContainerTermination(builder.appContext());
        destructor = new Destructor(resourceReferences, timeoutMgr, termination);
    }

    @Override
    protected void destroy() {
        boolean alreadyDestructed = destructor.destruct();
        if (alreadyDestructed) {
            throw new IllegalStateException(
                    "Already destructed! This should not occur unless destroy have been called directly!");
        }
    }

    /**
     * Make this instance retain a reference to the resource until it is destroyed.
     */
    void retainReference(SharedResource resource) {
        resourceReferences.retain(resource);
    }

    public ContainerTermination shutdown() {
        return termination;
    }

    public Injector guiceInjector() {
        return guiceInjector;
    }

    public Iterable<ServerProvider> serverProviders() {
        return serverProviders;
    }

    public Map<String, BindingSet<RequestHandler>> serverBindings() {
        return serverBindings;
    }

    public BindingSet<RequestHandler> serverBindings(String setName) {
        return serverBindings.get(setName);
    }

    public Map<String, BindingSet<RequestHandler>> clientBindings() {
        return clientBindings;
    }

    public BindingSet<RequestHandler> clientBindings(String setName) {
        return clientBindings.get(setName);
    }

    TimeoutManagerImpl timeoutManager() {
        return timeoutMgr;
    }

    @Override
    public ContainerSnapshot newReference(URI uri) {
        String name = bindingSetSelector.select(uri);
        if (name == null) {
            throw new NoBindingSetSelectedException(uri);
        }
        BindingSet<RequestHandler> serverBindings = serverBindings(name);
        BindingSet<RequestHandler> clientBindings = clientBindings(name);
        if (serverBindings == null || clientBindings == null) {
            throw new BindingSetNotFoundException(name);
        }
        return new ContainerSnapshot(this, serverBindings, clientBindings);
    }

    // NOTE: An instance of this class must never contain a reference to the outer class (ActiveContainer).
    static class Destructor {
        private final ResourcePool resourceReferences;
        private final TimeoutManagerImpl timeoutMgr;
        private final ContainerTermination termination;
        private final AtomicBoolean done = new AtomicBoolean();

        private Destructor(ResourcePool resourceReferences,
                           TimeoutManagerImpl timeoutMgr,
                           ContainerTermination termination) {
            this.resourceReferences = resourceReferences;
            this.timeoutMgr = timeoutMgr;
            this.termination = termination;
        }

        boolean destruct() {
            boolean alreadyDestructed = this.done.getAndSet(true);
            if (!alreadyDestructed) {
                resourceReferences.release();
                timeoutMgr.shutdown();
                termination.run();
            }
            return alreadyDestructed;
        }
    }
}
