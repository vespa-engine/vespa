// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.concurrent;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * @author jonmv
 */
public class StripedExecutorTest {

    private static final int workers = 1 << 5;
    private static final int values = 1 << 10;

    @Test
    public void testSerialization() {
        AtomicLong counter = new AtomicLong(0);
        List<Deque<Long>> sequences = new ArrayList<>();
        for (int j = 0; j < workers; j++)
            sequences.add(new ConcurrentLinkedDeque<>());

        StripedExecutor<Integer> executor = new StripedExecutor<>();
        for (int i = 0; i < values; i++)
            for (int j = 0; j < workers; j++) {
                Deque<Long> sequence = sequences.get(j);
                executor.execute(j, () -> sequence.add(counter.incrementAndGet()));
            }
        executor.shutdownAndWait();

        for (int j = 0; j < workers; j++) {
            assertEquals(values, sequences.get(j).size());
            assertArrayEquals(sequences.get(j).stream().sorted().toArray(Long[]::new),
                              sequences.get(j).toArray(Long[]::new));
        }
    }

}
