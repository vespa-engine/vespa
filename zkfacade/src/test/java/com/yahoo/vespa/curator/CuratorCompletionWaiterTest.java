// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator;

import com.yahoo.path.Path;
import com.yahoo.vespa.curator.mock.MockCurator;
import org.junit.Test;

import java.time.Duration;

import static org.junit.Assert.fail;

/**
 * @author Ulf Lilleengen
 */
public class CuratorCompletionWaiterTest {

    private static final Duration waitForAll = Duration.ofSeconds(1);

    @Test
    public void testCompletionWaiter() throws InterruptedException {
        Curator curator = new MockCurator();
        Curator.CompletionWaiter waiter = CuratorCompletionWaiter.createAndInitialize(curator, Path.createRoot(), "foo", "foo", waitForAll);
        Curator.CompletionWaiter notifier = CuratorCompletionWaiter.create(curator, Path.fromString("/foo"), "bar", waitForAll);
        Thread t1 = new Thread(() -> {
            try {
                waiter.awaitCompletion(Duration.ofSeconds(120));
            } catch (CompletionTimeoutException e) {
                fail("Waiting failed due to timeout");
            }
        });
        t1.start();
        notifier.notifyCompletion();
        t1.join();
    }

    @Test(expected = CompletionTimeoutException.class)
    public void testCompletionWaiterFailure() {
        Curator curator = new MockCurator();
        Curator.CompletionWaiter waiter = CuratorCompletionWaiter.createAndInitialize(curator, Path.createRoot(), "foo", "foo", waitForAll);
        waiter.awaitCompletion(Duration.ofMillis(100));
    }
}
