// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.google.inject.AbstractModule;
import com.yahoo.jdisc.application.BindingSet;
import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.jdisc.application.UriPattern;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.service.ServerProvider;
import com.yahoo.jdisc.test.NonWorkingRequestHandler;
import com.yahoo.jdisc.test.NonWorkingServerProvider;
import com.yahoo.jdisc.test.TestDriver;

import java.util.Iterator;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Simon Thoresen Hult
 */
public class ActiveContainerTestCase {

    @Test
    void requireThatGuiceAccessorWorks() {
        final Object obj = new Object();
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi(new AbstractModule() {

            @Override
            protected void configure() {
                bind(Object.class).toInstance(obj);
            }
        });
        ActiveContainer container = new ActiveContainer(driver.newContainerBuilder());
        assertSame(obj, container.guiceInjector().getInstance(Object.class));
        driver.close();
    }

    @Test
    void requireThatServerAccessorWorks() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        ContainerBuilder builder = driver.newContainerBuilder();
        ServerProvider foo = new NonWorkingServerProvider();
        builder.serverProviders().install(foo);
        ServerProvider bar = new NonWorkingServerProvider();
        builder.serverProviders().install(bar);
        ActiveContainer container = new ActiveContainer(builder);

        Iterator<ServerProvider> it = container.serverProviders().iterator();
        assertTrue(it.hasNext());
        assertSame(foo, it.next());
        assertTrue(it.hasNext());
        assertSame(bar, it.next());
        assertFalse(it.hasNext());
        driver.close();
    }

    @Test
    void requireThatServerBindingAccessorWorks() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        ContainerBuilder builder = driver.newContainerBuilder();
        RequestHandler foo = new NonWorkingRequestHandler();
        RequestHandler bar = new NonWorkingRequestHandler();
        builder.serverBindings().bind("http://host/foo", foo);
        builder.serverBindings("bar").bind("http://host/bar", bar);
        ActiveContainer container = new ActiveContainer(builder);

        Map<String, BindingSet<RequestHandler>> bindings = container.serverBindings();
        assertNotNull(bindings);
        assertEquals(2, bindings.size());

        BindingSet<RequestHandler> set = bindings.get(BindingSet.DEFAULT);
        assertNotNull(set);
        Iterator<Map.Entry<UriPattern, RequestHandler>> it = set.iterator();
        assertNotNull(it);
        assertTrue(it.hasNext());
        Map.Entry<UriPattern, RequestHandler> entry = it.next();
        assertNotNull(entry);
        assertEquals(new UriPattern("http://host/foo"), entry.getKey());
        assertSame(foo, entry.getValue());
        assertFalse(it.hasNext());

        assertNotNull(set = bindings.get("bar"));
        assertNotNull(it = set.iterator());
        assertTrue(it.hasNext());
        assertNotNull(entry = it.next());
        assertEquals(new UriPattern("http://host/bar"), entry.getKey());
        assertSame(bar, entry.getValue());
        assertFalse(it.hasNext());

        assertNotNull(bindings = container.clientBindings());
        assertEquals(1, bindings.size());
        assertNotNull(set = bindings.get(BindingSet.DEFAULT));
        assertNotNull(it = set.iterator());
        assertFalse(it.hasNext());

        driver.close();
    }

    @Test
    void requireThatClientBindingAccessorWorks() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        ContainerBuilder builder = driver.newContainerBuilder();
        RequestHandler foo = new NonWorkingRequestHandler();
        RequestHandler bar = new NonWorkingRequestHandler();
        builder.clientBindings().bind("http://host/foo", foo);
        builder.clientBindings("bar").bind("http://host/bar", bar);
        ActiveContainer container = new ActiveContainer(builder);

        Map<String, BindingSet<RequestHandler>> bindings = container.clientBindings();
        assertNotNull(bindings);
        assertEquals(2, bindings.size());

        BindingSet<RequestHandler> set = bindings.get(BindingSet.DEFAULT);
        assertNotNull(set);
        Iterator<Map.Entry<UriPattern, RequestHandler>> it = set.iterator();
        assertNotNull(it);
        assertTrue(it.hasNext());
        Map.Entry<UriPattern, RequestHandler> entry = it.next();
        assertNotNull(entry);
        assertEquals(new UriPattern("http://host/foo"), entry.getKey());
        assertSame(foo, entry.getValue());
        assertFalse(it.hasNext());

        assertNotNull(set = bindings.get("bar"));
        assertNotNull(it = set.iterator());
        assertTrue(it.hasNext());
        assertNotNull(entry = it.next());
        assertEquals(new UriPattern("http://host/bar"), entry.getKey());
        assertSame(bar, entry.getValue());
        assertFalse(it.hasNext());

        assertNotNull(bindings = container.serverBindings());
        assertEquals(1, bindings.size());
        assertNotNull(set = bindings.get(BindingSet.DEFAULT));
        assertNotNull(it = set.iterator());
        assertFalse(it.hasNext());

        driver.close();
    }

    @Test
    void requireThatDefaultBindingsAreAlwaysCreated() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        ContainerBuilder builder = driver.newContainerBuilder();
        ActiveContainer container = new ActiveContainer(builder);

        Map<String, BindingSet<RequestHandler>> bindings = container.serverBindings();
        assertNotNull(bindings);
        assertEquals(1, bindings.size());
        BindingSet<RequestHandler> set = bindings.get(BindingSet.DEFAULT);
        assertFalse(set.iterator().hasNext());
        driver.close();
    }
}
