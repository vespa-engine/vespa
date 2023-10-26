// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.test;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;

/**
 * @author Simon Thoresen Hult
 */
public class TestDriverIntegrationTest {

    @Test
    public void requireThatFactoryMethodsWork() {
        TestDriver.newApplicationBundleInstance("app-a.jar", false, new MyModule()).close();
        TestDriver.newInstance(TestDriver.newOsgiFramework(), "app-a.jar", false, new MyModule()).close();
    }

    private static class MyModule extends AbstractModule {

        final CountDownLatch latch = new CountDownLatch(1);

        @Override
        protected void configure() {
            bind(CountDownLatch.class).annotatedWith(Names.named("Init")).toInstance(latch);
            bind(CountDownLatch.class).annotatedWith(Names.named("Start")).toInstance(latch);
            bind(CountDownLatch.class).annotatedWith(Names.named("Stop")).toInstance(latch);
            bind(CountDownLatch.class).annotatedWith(Names.named("Destroy")).toInstance(latch);
        }
    }
}
