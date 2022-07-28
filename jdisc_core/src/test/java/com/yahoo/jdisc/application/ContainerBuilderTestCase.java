// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.application;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.name.Names;
import com.yahoo.jdisc.test.TestDriver;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;


/**
 * @author Simon Thoresen Hult
 */
public class ContainerBuilderTestCase {

    @Test
    void requireThatAccessorsWork() throws URISyntaxException {
        final Object obj = new Object();
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi(new AbstractModule() {

            @Override
            protected void configure() {
                bind(Object.class).toInstance(obj);
                bind(String.class).annotatedWith(Names.named("foo")).toInstance("foo");
            }
        });
        ContainerBuilder builder = driver.newContainerBuilder();
        assertSame(obj, builder.getInstance(Object.class));
        assertEquals("foo", builder.getInstance(Key.get(String.class, Names.named("foo"))));

        Object ctx = new Object();
        builder.setAppContext(ctx);
        assertSame(ctx, builder.appContext());

        assertTrue(driver.close());
    }

    @Test
    void requireThatContainerThreadFactoryIsBound() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        ContainerBuilder builder = driver.newContainerBuilder();
        assertSame(ContainerThread.Factory.class, builder.getInstance(ThreadFactory.class).getClass());
        assertTrue(driver.close());
    }

    @Test
    void requireThatThreadFactoryCanBeReconfigured() {
        final ThreadFactory factory = Executors.defaultThreadFactory();
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        ContainerBuilder builder = driver.newContainerBuilder();
        builder.guiceModules().install(new AbstractModule() {

            @Override
            protected void configure() {
                bind(ThreadFactory.class).toInstance(factory);
            }
        });
        assertSame(factory, builder.getInstance(ThreadFactory.class));
        assertTrue(driver.close());
    }

    @Test
    void requireThatBindingSetsAreCreatedOnDemand() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        ContainerBuilder builder = driver.newContainerBuilder();
        BindingRepository<?> repo = builder.serverBindings("foo");
        assertNotNull(repo);
        assertSame(repo, builder.serverBindings("foo"));
        assertNotNull(repo = builder.serverBindings("bar"));
        assertSame(repo, builder.serverBindings("bar"));
        assertNotNull(repo = builder.clientBindings("baz"));
        assertSame(repo, builder.clientBindings("baz"));
        assertNotNull(repo = builder.clientBindings("cox"));
        assertSame(repo, builder.clientBindings("cox"));
        driver.close();
    }

    @Test
    void requireThatSafeClassCastWorks() {
        ContainerBuilder.safeClassCast(Integer.class, Integer.class);
    }

    @Test
    void requireThatSafeClassCastThrowsIllegalArgument() {
        try {
            ContainerBuilder.safeClassCast(Integer.class, Double.class);
            fail();
        } catch (IllegalArgumentException e) {

        }
    }

    @Test
    void requireThatSafeStringSplitWorks() {
        assertTrue(ContainerBuilder.safeStringSplit(new Object(), ",").isEmpty());
        assertTrue(ContainerBuilder.safeStringSplit("", ",").isEmpty());
        assertTrue(ContainerBuilder.safeStringSplit(" \f\n\r\t", ",").isEmpty());
        assertEquals(Arrays.asList("foo"), ContainerBuilder.safeStringSplit("foo", ","));
        assertEquals(Arrays.asList("foo"), ContainerBuilder.safeStringSplit(" foo", ","));
        assertEquals(Arrays.asList("foo"), ContainerBuilder.safeStringSplit("foo ", ","));
        assertEquals(Arrays.asList("foo"), ContainerBuilder.safeStringSplit("foo, ", ","));
        assertEquals(Arrays.asList("foo"), ContainerBuilder.safeStringSplit("foo ,", ","));
        assertEquals(Arrays.asList("foo", "bar"), ContainerBuilder.safeStringSplit("foo, bar", ","));
    }
}
