// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.yahoo.jdisc.Container;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.test.TestDriver;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen Hult</a>
 */
@SuppressWarnings("UnusedAssignment")
public class ActiveContainerFinalizerTest {

    @Test
    public void requireThatMissingContainerReleaseDoesNotPreventShutdown() throws InterruptedException {
        final TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        driver.activateContainer(driver.newContainerBuilder());
        Container container = driver.newReference(URI.create("scheme://host"));
        assertNotNull(container);

        final Termination termination = new Termination();
        driver.activateContainer(null).notifyTermination(termination);
        assertFalse(termination.await(100, TimeUnit.MILLISECONDS));

        container = null; // intentionally doing this instead of container.release()
        assertTrue(termination.await(600, TimeUnit.SECONDS));
        assertTrue(driver.close());
    }

    @Test
    public void requireThatMissingRequestReleaseDoesNotPreventShutdown() throws InterruptedException {
        final TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        driver.activateContainer(driver.newContainerBuilder());
        Request request = new Request(driver, URI.create("scheme://host"));
        assertNotNull(request);

        final Termination termination = new Termination();
        driver.activateContainer(null).notifyTermination(termination);
        assertFalse(termination.await(100, TimeUnit.MILLISECONDS));

        request = null; // intentionally doing this instead of request.release()
        assertTrue(termination.await(600, TimeUnit.SECONDS));
        assertTrue(driver.close());
    }

    private static class Termination implements Runnable {

        volatile boolean done;

        @Override
        public void run() {
            done = true;
        }

        boolean await(final int timeout, final TimeUnit unit) throws InterruptedException {
            final long timeoutAt = System.currentTimeMillis() + unit.toMillis(timeout);
            while (!done) {
                if (System.currentTimeMillis() > timeoutAt) {
                    return false;
                }
                Thread.sleep(10);
                System.gc();
            }
            return true;
        }
    }
}
