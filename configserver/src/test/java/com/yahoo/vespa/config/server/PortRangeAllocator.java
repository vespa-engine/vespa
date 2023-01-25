// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Range;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Allocates port ranges for all configserver tests.
 *
 * @author Ulf Lilleengen
 */
public class PortRangeAllocator {
    private final static PortRange portRange = new PortRange();

    // Get the next port from a pre-allocated range
    public static int findAvailablePort() throws InterruptedException {
        return portRange.next();
    }

    public static void releasePort(int port) {
        portRange.release(port);
    }

    private static class PortRange {
        private final Set<Integer> takenPorts = new HashSet<>();
        private final Deque<Integer> freePorts = new ArrayDeque<>();
        private static final int first = 18651;
        private static final int last = 18899; // see: factory/doc/port-ranges

        PortRange() {
            freePorts.addAll(ContiguousSet.create(Range.closed(first, last), DiscreteDomain.integers()));
        }

        synchronized int next() throws InterruptedException {
            if (freePorts.isEmpty()) {
                wait(600_000);
                if (freePorts.isEmpty()) {
                    throw new RuntimeException("no more ports in range " + first + "-" + last);
                }
            }
            int port = freePorts.pop();
            takenPorts.add(port);
            return port;
        }

        synchronized void release(int port) {
            if (port < first || port > last) {
                throw new RuntimeException("trying to release port outside valid range " + port);
            }
            if (!takenPorts.contains(port)) {
                throw new RuntimeException("trying to release port never acquired " + port);
            }
            takenPorts.remove(port);
            freePorts.push(port);
            notify();
        }
    }

}
