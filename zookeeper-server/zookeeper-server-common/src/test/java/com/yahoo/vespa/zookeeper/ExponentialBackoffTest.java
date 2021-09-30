// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

/**
 * @author mpolden
 */
public class ExponentialBackoffTest {

    @Test
    public void delay() {
        ExponentialBackoff b = new ExponentialBackoff(Duration.ofSeconds(1), Duration.ofSeconds(10), new Random(1000));
        assertEquals(List.of(Duration.ofMillis(1210),
                             Duration.ofMillis(2150),
                             Duration.ofMillis(4340),
                             Duration.ofMillis(2157),
                             Duration.ofMillis(4932)),
                     IntStream.rangeClosed(1, 5)
                              .mapToObj(b::delay)
                              .collect(Collectors.toList()));
    }

}
