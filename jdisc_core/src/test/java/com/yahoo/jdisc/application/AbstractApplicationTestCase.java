// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.application;

import com.google.inject.Inject;
import com.yahoo.jdisc.service.CurrentContainer;
import com.yahoo.jdisc.test.TestDriver;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * @author Simon Thoresen Hult
 */
public class AbstractApplicationTestCase {

    @Test
    void requireThatContainerApiIsAvailable() {
        TestDriver driver = TestDriver.newInjectedApplicationInstance(MyApplication.class);
        MyApplication app = (MyApplication) driver.application();
        app.activateContainer(app.newContainerBuilder());
        assertNotNull(app.container());
        assertTrue(driver.close());
    }

    @Test
    void requireThatDestroySignalsTermination() {
        TestDriver driver = TestDriver.newInjectedApplicationInstance(MyApplication.class);
        MyApplication app = (MyApplication) driver.application();
        assertFalse(app.isTerminated());
        assertTrue(driver.close());
        assertTrue(app.isTerminated());
    }

    @Test
    void requireThatTerminationCanBeWaitedForWithTimeout() throws InterruptedException {
        TestDriver driver = TestDriver.newInjectedApplicationInstance(MyApplication.class);
        final MyApplication app = (MyApplication) driver.application();
        final CountDownLatch latch = new CountDownLatch(1);
        Executors.newSingleThreadExecutor().execute(new Runnable() {

            @Override
            public void run() {
                try {
                    app.awaitTermination(600, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                latch.countDown();
            }
        });
        assertFalse(latch.await(100, TimeUnit.MILLISECONDS));
        assertTrue(driver.close());
        assertTrue(latch.await(600, TimeUnit.SECONDS));
    }

    @Test
    void requireThatTerminationCanBeWaitedForWithoutTimeout() throws InterruptedException {
        TestDriver driver = TestDriver.newInjectedApplicationInstance(MyApplication.class);
        final MyApplication app = (MyApplication) driver.application();
        final CountDownLatch latch = new CountDownLatch(1);
        Executors.newSingleThreadExecutor().execute(new Runnable() {

            @Override
            public void run() {
                try {
                    app.awaitTermination();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                latch.countDown();
            }
        });
        assertFalse(latch.await(100, TimeUnit.MILLISECONDS));
        assertTrue(driver.close());
        assertTrue(latch.await(600, TimeUnit.SECONDS));
    }

    private static class MyApplication extends AbstractApplication {

        @Inject
        public MyApplication(BundleInstaller bundleInstaller, ContainerActivator activator,
                             CurrentContainer container) {
            super(bundleInstaller, activator, container);
        }

        @Override
        public void start() {

        }
    }
}
