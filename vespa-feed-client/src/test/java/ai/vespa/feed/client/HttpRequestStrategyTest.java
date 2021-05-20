// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

class HttpRequestStrategyTest {

    @Test
    void test() throws InterruptedException {
        AtomicLong c = new AtomicLong();
        class Counter {
            long d;
            synchronized long next() { return ++d; }
        }
        Counter d = new Counter();
        int n = 2;
        ExecutorService executor = Executors.newFixedThreadPool(n);
        Instant now = Instant.now();
        for (int i = 0; i < n; i++) {
            executor.submit(() -> {
                //while (c.incrementAndGet() < 1e8);
                //while (d.next() < 1e8);
            });
        }
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.DAYS);
        Assertions.fail("ms: " + Duration.between(now, Instant.now()).toMillis());
    };

}
