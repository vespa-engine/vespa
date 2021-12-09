// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.application;

import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Module;
import com.yahoo.jdisc.Container;
import com.yahoo.jdisc.handler.RequestHandler;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * <p>This is the inactive, mutable {@link Container}. Because it requires references to the application internals, it
 * should always be injected by guice or created by calling {@link ContainerActivator#newContainerBuilder()}. Once the
 * builder has been configured, it is activated by calling {@link
 * ContainerActivator#activateContainer(ContainerBuilder)}. You may use the {@link #setAppContext(Object)} method to
 * attach an arbitrary object to a Container, which will be available in the corresponding {@link
 * DeactivatedContainer}.</p>
 *
 * @author Simon Thoresen Hult
 */
public class ContainerBuilder {

    private final GuiceRepository guiceModules = new GuiceRepository();
    private final ServerRepository serverProviders = new ServerRepository(guiceModules);
    private final Map<String, BindingRepository<RequestHandler>> serverBindings = new HashMap<>();
    private final Map<String, BindingRepository<RequestHandler>> clientBindings = new HashMap<>();
    private Object appContext = null;

    public ContainerBuilder(Iterable<Module> guiceModules) {
        this.guiceModules.installAll(guiceModules);
        this.guiceModules.install(new AbstractModule() {

            @Override
            public void configure() {
                bind(ContainerBuilder.class).toInstance(ContainerBuilder.this);
            }
        });
        this.serverBindings.put(BindingSet.DEFAULT, new BindingRepository<>());
        this.clientBindings.put(BindingSet.DEFAULT, new BindingRepository<>());
    }

    public void setAppContext(Object ctx) {
        appContext = ctx;
    }

    public Object appContext() {
        return appContext;
    }

    public GuiceRepository guiceModules() {
        return guiceModules;
    }

    public <T> T getInstance(Key<T> key) {
        return guiceModules.getInstance(key);
    }

    public <T> T getInstance(Class<T> type) {
        return guiceModules.getInstance(type);
    }

    public ServerRepository serverProviders() {
        return serverProviders;
    }

    public BindingRepository<RequestHandler> serverBindings() {
        return serverBindings.get(BindingSet.DEFAULT);
    }

    public BindingRepository<RequestHandler> serverBindings(String setName) {
        BindingRepository<RequestHandler> ret = serverBindings.get(setName);
        if (ret == null) {
            ret = new BindingRepository<>();
            serverBindings.put(setName, ret);
        }
        return ret;
    }

    public Map<String, BindingSet<RequestHandler>> activateServerBindings() {
        Map<String, BindingSet<RequestHandler>> ret = new HashMap<>();
        for (Map.Entry<String, BindingRepository<RequestHandler>> entry : serverBindings.entrySet()) {
            ret.put(entry.getKey(), entry.getValue().activate());
        }
        return ImmutableMap.copyOf(ret);
    }

    public BindingRepository<RequestHandler> clientBindings() {
        return clientBindings.get(BindingSet.DEFAULT);
    }

    public BindingRepository<RequestHandler> clientBindings(String setName) {
        BindingRepository<RequestHandler> ret = clientBindings.get(setName);
        if (ret == null) {
            ret = new BindingRepository<>();
            clientBindings.put(setName, ret);
        }
        return ret;
    }

    public Map<String, BindingSet<RequestHandler>> activateClientBindings() {
        Map<String, BindingSet<RequestHandler>> ret = new HashMap<>();
        for (Map.Entry<String, BindingRepository<RequestHandler>> entry : clientBindings.entrySet()) {
            ret.put(entry.getKey(), entry.getValue().activate());
        }
        return ImmutableMap.copyOf(ret);
    }

    @SuppressWarnings({ "unchecked" })
    public static <T> Class<T> safeClassCast(Class<T> baseClass, Class<?> someClass) {
        if (!baseClass.isAssignableFrom(someClass)) {
            throw new IllegalArgumentException("Expected " + baseClass.getName() + ", got " +
                                               someClass.getName() + ".");
        }
        return (Class<T>)someClass;
    }

    public static List<String> safeStringSplit(Object obj, String delim) {
        if (!(obj instanceof String)) {
            return Collections.emptyList();
        }
        List<String> lst = new LinkedList<>();
        for (String str : ((String)obj).split(delim)) {
            str = str.trim();
            if (!str.isEmpty()) {
                lst.add(str);
            }
        }
        return lst;
    }

}
