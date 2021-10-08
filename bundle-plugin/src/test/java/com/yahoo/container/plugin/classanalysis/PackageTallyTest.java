// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.classanalysis;

import org.junit.Test;

import java.util.Set;

import static java.util.Collections.emptyMap;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author gjoranv
 */
public class PackageTallyTest {

    @Test
    public void referenced_packages_missing_from_are_detected() {
        PackageTally tally = new PackageTally(emptyMap(), Set.of("p1", "java.util", "com.yahoo.api.annotations", "missing"));
        Set<String> missingPackages = tally.referencedPackagesMissingFrom(Set.of("p1"));
        assertThat(missingPackages, is(Set.of("missing")));
    }

}
