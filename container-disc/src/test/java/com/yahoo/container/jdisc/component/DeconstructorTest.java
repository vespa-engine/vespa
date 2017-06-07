// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.component;

import com.yahoo.component.AbstractComponent;
import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.jdisc.SharedResource;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author gjoranv
 */
public class DeconstructorTest {
    public static Deconstructor deconstructor;

    @Before
    public void init() {
        deconstructor = new Deconstructor(false);
    }

    @Test
    public void require_abstract_component_destructed() throws InterruptedException {
        TestAbstractComponent abstractComponent = new TestAbstractComponent();
        // Done by executor, so it takes some time even with a 0 delay.
        deconstructor.deconstruct(abstractComponent);
        int cnt = 0;
        while (! abstractComponent.destructed && (cnt++ < 10)) {
            Thread.sleep(10);
        }
        assertTrue(abstractComponent.destructed);
    }

    @Test
    public void test_that_throwing_destruct_is_silent() throws InterruptedException {
        TestThrowingAbstractComponent abstractComponent = new TestThrowingAbstractComponent();
        // Done by executor, so it takes some time even with a 0 delay.
        deconstructor.deconstruct(abstractComponent);
        for (int cnt = 0;(abstractComponent.triedDestructed == 0) && (cnt < 10); cnt++) {
            Thread.sleep(10);
        }
        assertEquals(1l, abstractComponent.triedDestructed);
        long firstThreadId = abstractComponent.destructorThreadId;
        String firstThreadName = abstractComponent.destructorThreadName;
        assertEquals("deconstructor-1-thread-1", firstThreadName);

        deconstructor.deconstruct(abstractComponent);

        for (int cnt = 0;(abstractComponent.triedDestructed == 1) && (cnt < 10); cnt++) {
            Thread.sleep(10);
        }
        assertEquals(2l, abstractComponent.triedDestructed);
        assertEquals(firstThreadId, abstractComponent.destructorThreadId);
        assertEquals(firstThreadName, abstractComponent.destructorThreadName);
    }

    @Test
    public void require_provider_destructed() {
        TestProvider provider = new TestProvider();
        deconstructor.deconstruct(provider);
        assertTrue(provider.destructed);
    }

    @Test
    public void require_shared_resource_released() {
        TestSharedResource sharedResource = new TestSharedResource();
        deconstructor.deconstruct(sharedResource);
        assertTrue(sharedResource.released);
    }

    private static class TestAbstractComponent extends AbstractComponent {
        boolean destructed = false;
        @Override public void deconstruct() { destructed = true; }
    }

    private static class TestProvider implements Provider<Void> {
        boolean destructed = false;

        @Override public Void get() { return null; }
        @Override public void deconstruct() { destructed = true; }
    }

    private static class TestSharedResource implements SharedResource {
        boolean released = false;

        @Override public ResourceReference refer() { return null; }
        @Override public void release() { released = true; }
    }

    private static class TestThrowingAbstractComponent extends AbstractComponent {
        int triedDestructed = 0;
        String destructorThreadName;
        long destructorThreadId = 0;
        @Override public void deconstruct() {
            destructorThreadName = Thread.currentThread().getName();
            destructorThreadId = Thread.currentThread().getId();
            if ( triedDestructed++ == 0) {
                throw new NullPointerException();
            }
        }
    }

}
