// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bjorncs
 */
class CapabilitySetTest {

    @Test
    void contains_all_capabilities() {
        SortedSet<String> expectedNames = Arrays.stream(Capability.values())
                .map(Capability::asString)
                .collect(Collectors.toCollection(TreeSet::new));
        SortedSet<String> actualNames = CapabilitySet.all().toNames();
        assertEquals(expectedNames, actualNames);
    }

}
