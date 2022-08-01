// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.classanalysis;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author gjoranv
 */
public class PackageTallyTest {

    @Test
    void referenced_packages_missing_from_available_packages_are_detected() {
        PackageTally tally = new PackageTally(Map.of(), Set.of("p1", "java.util", "com.yahoo.api.annotations", "missing"));
        Set<String> missingPackages = tally.referencedPackagesMissingFrom(Set.of("p1"));
        assertEquals(Set.of("missing"), missingPackages);
    }

}
