// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.client;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.yahoo.jdisc.application.BundleInstaller;
import com.yahoo.jdisc.application.ContainerActivator;
import com.yahoo.jdisc.service.CurrentContainer;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Simon Thoresen Hult
 */
public class AbstractClientApplicationTestCase {

    @Test
    void requireThatApplicationCanBeShutdown() throws Exception {
        MyDriver driver = newDriver();
        assertFalse(driver.awaitDone(100, TimeUnit.MILLISECONDS));
        assertTrue(driver.awaitApp(600, TimeUnit.SECONDS));
        driver.app.shutdown();
        assertTrue(driver.app.isShutdown());
        assertTrue(driver.close());
    }

    @Test
    void requireThatShutdownCanBeWaitedForWithTimeout() throws Exception {
        final MyDriver driver = newDriver();
        assertFalse(driver.awaitDone(100, TimeUnit.MILLISECONDS));
        assertTrue(driver.awaitApp(600, TimeUnit.SECONDS));

        final CountDownLatch latch = new CountDownLatch(1);
        Executors.newSingleThreadExecutor().submit(new Callable<Boolean>() {

            @Override
            public Boolean call() throws Exception {
                driver.app.awaitShutdown(600, TimeUnit.SECONDS);
                latch.countDown();
                return Boolean.TRUE;
            }
        });
        assertFalse(latch.await(100, TimeUnit.MILLISECONDS));
        driver.app.shutdown();
        assertTrue(driver.close());
        assertTrue(latch.await(600, TimeUnit.SECONDS));
    }

    @Test
    void requireThatShutdownCanBeWaitedForWithoutTimeout() throws Exception {
        final MyDriver driver = newDriver();
        assertFalse(driver.awaitDone(100, TimeUnit.MILLISECONDS));
        assertTrue(driver.awaitApp(600, TimeUnit.SECONDS));

        final CountDownLatch latch = new CountDownLatch(1);
        Executors.newSingleThreadExecutor().submit(new Callable<Boolean>() {

            @Override
            public Boolean call() throws Exception {
                driver.app.awaitShutdown();
                latch.countDown();
                return Boolean.TRUE;
            }
        });
        assertFalse(latch.await(100, TimeUnit.MILLISECONDS));
        driver.app.shutdown();
        assertTrue(driver.close());
        assertTrue(latch.await(600, TimeUnit.SECONDS));
    }

    private static MyDriver newDriver() {
        final MyDriver driver = new MyDriver();
        driver.done = Executors.newSingleThreadExecutor().submit(new Callable<Boolean>() {

            @Override
            public Boolean call() throws Exception {
                ClientDriver.runApplication(MyApplication.class, driver);
                return Boolean.TRUE;
            }
        });
        return driver;
    }

    private static class MyDriver extends AbstractModule {

        final CountDownLatch appLatch = new CountDownLatch(1);
        Future<Boolean> done;
        MyApplication app;

        @Override
        protected void configure() {
            bind(MyDriver.class).toInstance(this);
        }

        boolean awaitApp(int timeout, TimeUnit unit) throws InterruptedException {
            return appLatch.await(timeout, unit);
        }

        boolean awaitDone(int timeout, TimeUnit unit) throws ExecutionException, InterruptedException {
            try {
                done.get(timeout, unit);
                return app.isTerminated();
            } catch (TimeoutException e) {
                return false;
            }
        }

        boolean close() throws ExecutionException, InterruptedException {
            return awaitDone(600, TimeUnit.SECONDS);
        }
    }

    private static class MyApplication extends AbstractClientApplication {

        @Inject
        MyApplication(BundleInstaller bundleInstaller, ContainerActivator activator,
                      CurrentContainer container, MyDriver driver) {
            super(bundleInstaller, activator, container);
            driver.app = this;
            driver.appLatch.countDown();
        }

        @Override
        public void start() {

        }
    }
}
