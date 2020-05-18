// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.config.subscription.ConfigSourceSet;
import org.junit.Test;

import java.util.*;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.*;

/**
 * Tests for the JRTConnectionPool class.
 *
 * @author Gunnar Gauslaa Bergem
 * @author hmusum
 */
public class JRTConnectionPoolTest {
    private static final List<String> sources = new ArrayList<>((Arrays.asList("host0", "host1", "host2")));

    /**
     * Tests that hash-based selection through the list works.
     */
    @Test
    public void test_random_selection_of_sourceBasicHashBasedSelection() {
        JRTConnectionPool sourcePool = new JRTConnectionPool(sources);
        assertThat(sourcePool.toString(), is("Address: host0\nAddress: host1\nAddress: host2\n"));

        Map<String, Integer> sourceOccurrences = new HashMap<>();
        for (int i = 0; i < 1000; i++) {
            final String address = sourcePool.setNewCurrentConnection().getAddress();
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

        List<String> twoSources = new ArrayList<>();

        twoSources.add("host0");
        twoSources.add("host1");
        JRTConnectionPool sourcePool = new JRTConnectionPool(twoSources);

        int count = 1000;
        for (int i = 0; i < count; i++) {
            String address = sourcePool.setNewCurrentConnection().getAddress();
            if (timesUsed.containsKey(address)) {
                int times = timesUsed.get(address);
                timesUsed.put(address, times + 1);
            } else {
                timesUsed.put(address, 1);
            }
        }
        assertConnectionDistributionIsFair(timesUsed);
    }

    // Tests that the number of times each connection is used is close to equal
    private void assertConnectionDistributionIsFair(Map<String, Integer> connectionsUsedPerHost) {
        double devianceDueToRandomSourceSelection = 0.14;
        final int size = 1000;
        int minHostCount = (int) (size/2 * (1 - devianceDueToRandomSourceSelection));
        int maxHostCount = (int) (size/2 * (1 + devianceDueToRandomSourceSelection));

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
        List<String> twoSources = new ArrayList<>();

        twoSources.add("host0");
        twoSources.add("host1");
        JRTConnectionPool sourcePool = new JRTConnectionPool(twoSources);

        ConfigSourceSet sourcesBefore = sourcePool.getSourceSet();

        // Update to the same set, should be equal
        sourcePool.updateSources(twoSources);
        assertThat(sourcesBefore, is(sourcePool.getSourceSet()));

        // Update to new set
        List<String> newSources = new ArrayList<>();
        newSources.add("host2");
        newSources.add("host3");
        sourcePool.updateSources(newSources);
        ConfigSourceSet newSourceSet = sourcePool.getSourceSet();
        assertNotNull(newSourceSet);
        assertThat(newSourceSet.getSources().size(), is(2));
        assertThat(newSourceSet, is(not(sourcesBefore)));
        assertTrue(newSourceSet.getSources().contains("host2"));
        assertTrue(newSourceSet.getSources().contains("host3"));

        // Update to new set with just one host
        List<String> newSources2 = new ArrayList<>();
        newSources2.add("host4");
        sourcePool.updateSources(newSources2);
        ConfigSourceSet newSourceSet2 = sourcePool.getSourceSet();
        assertNotNull(newSourceSet2);
        assertThat(newSourceSet2.getSources().size(), is(1));
        assertThat(newSourceSet2, is(not(newSourceSet)));
        assertTrue(newSourceSet2.getSources().contains("host4"));
    }
}
