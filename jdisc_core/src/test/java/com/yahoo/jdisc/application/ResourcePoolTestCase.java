// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.application;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.yahoo.jdisc.AbstractResource;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.jdisc.test.TestDriver;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


/**
 * @author Simon Thoresen Hult
 */
public class ResourcePoolTestCase {

    @Test
    public void requireThatAddReturnsArgument() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        MyResource foo = new MyResource();
        assertSame(foo, new ResourcePool(driver.newContainerBuilder()).add(foo));
        assertTrue(driver.close());
    }

    @Test
    public void requireThatAddDoesNotRetainArgument() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        MyResource foo = new MyResource();
        assertEquals(1, foo.retainCount());
        new ResourcePool(driver.newContainerBuilder()).add(foo);
        assertEquals(1, foo.retainCount());
        assertTrue(driver.close());
    }

    @Test
    public void requireThatAddCanBeUsedWithoutContainerBuilder() {
        new ResourcePool().add(new MyResource());
    }

    @Test
    public void requireThatRetainReturnsArgument() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        MyResource foo = new MyResource();
        assertSame(foo, new ResourcePool(driver.newContainerBuilder()).retain(foo));
        assertTrue(driver.close());
    }

    @Test
    public void requireThatRetainRetainsArgument() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        MyResource foo = new MyResource();
        assertEquals(1, foo.retainCount());
        new ResourcePool(driver.newContainerBuilder()).retain(foo);
        assertEquals(2, foo.retainCount());
        assertTrue(driver.close());
    }

    @Test
    public void requireThatRetainCanBeUsedWithoutContainerBuilder() {
        new ResourcePool().retain(new MyResource());
    }

    @Test
    public void requireThatGetReturnsBoundInstance() {
        final MyResource foo = new MyResource();
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi(new AbstractModule() {

            @Override
            protected void configure() {
                bind(MyResource.class).toInstance(foo);
            }
        });
        ResourcePool pool = new ResourcePool(driver.newContainerBuilder());
        assertSame(foo, pool.get(MyResource.class));
        assertSame(foo, pool.get(Key.get(MyResource.class)));
        assertTrue(driver.close());
    }

    @Test
    public void requireThatGetDoesNotRetainArgument() {
        final MyResource foo = new MyResource();
        assertEquals(1, foo.retainCount());
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi(new AbstractModule() {

            @Override
            protected void configure() {
                bind(MyResource.class).toInstance(foo);
            }
        });
        ResourcePool pool = new ResourcePool(driver.newContainerBuilder());
        pool.get(MyResource.class);
        assertEquals(1, foo.retainCount());
        pool.get(Key.get(MyResource.class));
        assertEquals(1, foo.retainCount());
        assertTrue(driver.close());
    }

    @Test
    public void requireThatGetCanNotBeUsedWithoutContainerBuilder() {
        ResourcePool pool = new ResourcePool();
        try {
            pool.get(MyResource.class);
            fail();
        } catch (NullPointerException e) {

        }
        try {
            pool.get(Key.get(MyResource.class));
            fail();
        } catch (NullPointerException e) {

        }
    }

    @Test
    public void requireThatResourcesAreReleasedOnDestroy() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();

        ResourcePool pool = new ResourcePool(driver.newContainerBuilder());
        MyResource foo = pool.add(new MyResource());
        MyResource bar = pool.add(new MyResource());
        MyResource baz = pool.add(new MyResource());
        assertEquals(1, pool.retainCount());
        assertEquals(1, foo.retainCount());
        assertEquals(1, bar.retainCount());
        assertEquals(1, baz.retainCount());

        final ResourceReference secondPoolReference = pool.refer();
        assertEquals(2, pool.retainCount());
        assertEquals(1, foo.retainCount());
        assertEquals(1, bar.retainCount());
        assertEquals(1, baz.retainCount());

        secondPoolReference.close();
        assertEquals(1, pool.retainCount());
        assertEquals(1, foo.retainCount());
        assertEquals(1, bar.retainCount());
        assertEquals(1, baz.retainCount());

        pool.release();
        assertEquals(0, pool.retainCount());
        assertEquals(0, foo.retainCount());
        assertEquals(0, bar.retainCount());
        assertEquals(0, baz.retainCount());

        assertTrue(driver.close());
    }

    @Test
    public void requireThatAutoCloseCallsRelease() throws Exception {
        MyResource foo = new MyResource();
        assertEquals(1, foo.retainCount());
        try (ResourcePool pool = new ResourcePool()) {
            pool.retain(foo);
            assertEquals(2, foo.retainCount());
        }
        assertEquals(1, foo.retainCount());
    }

    private static class MyResource extends AbstractResource {

    }
}
