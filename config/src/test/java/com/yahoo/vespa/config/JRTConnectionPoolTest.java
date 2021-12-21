// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.config.subscription.ConfigSourceSet;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the JRTConnectionPool class.
 *
 * @author Gunnar Gauslaa Bergem
 * @author hmusum
 */
public class JRTConnectionPoolTest {

    private static final ConfigSourceSet sources = new ConfigSourceSet(List.of("host0", "host1", "host2"));

    @Test
    public void test_random_selection_of_source() {
        JRTConnectionPool sourcePool = new JRTConnectionPool(sources);
        assertEquals("host0,host1,host2",
                     sourcePool.getSources().stream().map(JRTConnection::getAddress).collect(Collectors.joining(",")));

        Map<String, Integer> sourceOccurrences = new HashMap<>();
        for (int i = 0; i < 1000; i++) {
            String address = sourcePool.switchConnection().getAddress();
            if (sourceOccurrences.containsKey(address)) {
                sourceOccurrences.put(address, sourceOccurrences.get(address) + 1);
            } else {
                sourceOccurrences.put(address, 1);
            }
        }
        for (int i = 0; i < sourcePool.getSize(); i++) {
            assertTrue(sourceOccurrences.get(sourcePool.getSources().get(i).getAddress()) > 200);
        }
    }

    /**
     * Tests that when there are two sources and several clients
     * the sources will be chosen with about the same probability.
     */
    @Test
    public void testManySources() {
        Map<String, Integer> timesUsed = new LinkedHashMap<>();

        ConfigSourceSet twoSources = new ConfigSourceSet(List.of("host0", "host1"));
        JRTConnectionPool sourcePool = new JRTConnectionPool(twoSources);

        int count = 1000;
        for (int i = 0; i < count; i++) {
            String address = sourcePool.switchConnection().getAddress();
            if (timesUsed.containsKey(address)) {
                int times = timesUsed.get(address);
                timesUsed.put(address, times + 1);
            } else {
                timesUsed.put(address, 1);
            }
        }
        assertConnectionDistributionIsFair(timesUsed);
        sourcePool.close();
    }

    // Tests that the number of times each connection is used is close to equal
    private void assertConnectionDistributionIsFair(Map<String, Integer> connectionsUsedPerHost) {
        double deviationDueToRandomSourceSelection = 0.15;
        final int size = 1000;
        int minHostCount = (int) (size/2 * (1 - deviationDueToRandomSourceSelection));
        int maxHostCount = (int) (size/2 * (1 + deviationDueToRandomSourceSelection));

        for (Map.Entry<String, Integer> entry : connectionsUsedPerHost.entrySet()) {
            Integer timesUsed = entry.getValue();
            assertTrue("Host 0 used " + timesUsed + " times, expected to be < " + maxHostCount, timesUsed < maxHostCount);
            assertTrue("Host 0 used " + timesUsed + " times, expected to be > " + minHostCount, timesUsed > minHostCount);
        }
    }

    /**
     * Tests that updating config sources works.
     */
    @Test
    public void updateSources() {
        ConfigSourceSet twoSources = new ConfigSourceSet(List.of("host0", "host1"));
        JRTConnectionPool sourcePool = new JRTConnectionPool(twoSources);

        ConfigSourceSet sourcesBefore = sourcePool.getSourceSet();

        // Update to the same set, should be equal
        sourcePool.updateSources(twoSources);
        assertEquals(sourcePool.getSourceSet(), sourcesBefore);

        // Update to new set
        List<String> newSources = new ArrayList<>();
        newSources.add("host2");
        newSources.add("host3");
        sourcePool.updateSources(newSources);
        ConfigSourceSet newSourceSet = sourcePool.getSourceSet();
        assertNotNull(newSourceSet);
        assertEquals(2, newSourceSet.getSources().size());
        assertNotEquals(sourcesBefore, newSourceSet);
        assertTrue(newSourceSet.getSources().contains("host2"));
        assertTrue(newSourceSet.getSources().contains("host3"));

        // Update to new set with just one host
        List<String> newSources2 = new ArrayList<>();
        newSources2.add("host4");
        sourcePool.updateSources(newSources2);
        ConfigSourceSet newSourceSet2 = sourcePool.getSourceSet();
        assertNotNull(newSourceSet2);
        assertEquals(1, newSourceSet2.getSources().size());
        assertNotEquals(newSourceSet, newSourceSet2);
        assertTrue(newSourceSet2.getSources().contains("host4"));

        sourcePool.close();
    }

    @Test
    public void testFailingSources() {
        ConfigSourceSet sources = new ConfigSourceSet(List.of("host0", "host1"));
        JRTConnectionPool connectionPool = new JRTConnectionPool(sources);

        Connection firstConnection = connectionPool.getCurrent();

        // Should change connection, not getting first connection as new
        JRTConnection secondConnection = failAndGetNewConnection(connectionPool, firstConnection);
        assertNotEquals(firstConnection, secondConnection);

        // Should change connection, not getting second connection as new
        JRTConnection thirdConnection = failAndGetNewConnection(connectionPool, secondConnection);
        // Fail a few more times with old connection, as will happen when there are multiple subscribers
        // Connection should not change
        assertEquals(thirdConnection, failAndGetNewConnection(connectionPool, secondConnection));
        assertEquals(thirdConnection, failAndGetNewConnection(connectionPool, secondConnection));
        assertEquals(thirdConnection, failAndGetNewConnection(connectionPool, secondConnection));
        assertNotEquals(secondConnection, thirdConnection);

        // Should change connection, not getting third connection as new
        JRTConnection currentConnection = failAndGetNewConnection(connectionPool, thirdConnection);
        assertNotEquals(thirdConnection, currentConnection);

        // Should change connection, not getting current connection as new
        JRTConnection currentConnection2 = failAndGetNewConnection(connectionPool, currentConnection);
        assertNotEquals(currentConnection, currentConnection2);

        connectionPool.close();
    }

    private JRTConnection failAndGetNewConnection(JRTConnectionPool connectionPool, Connection failingConnection) {
        connectionPool.switchConnection(failingConnection);
        return connectionPool.getCurrent();
    }

}
