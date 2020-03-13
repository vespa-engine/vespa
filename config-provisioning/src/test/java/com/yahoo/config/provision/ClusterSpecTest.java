// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.google.common.testing.EqualsTester;
import com.yahoo.component.Version;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

/**
 * @author Ulf Lilleengen
 */
public class ClusterSpecTest {

    @Test
    public void testIdEquals() {
        new EqualsTester()
                .addEqualityGroup(ClusterSpec.Id.from("id1"), ClusterSpec.Id.from("id1"))
                .addEqualityGroup(ClusterSpec.Id.from("id2"))
                .addEqualityGroup(ClusterSpec.Id.from("id3"))
                .testEquals();
    }

    @Test
    public void testGroupEquals() {
        new EqualsTester()
                .addEqualityGroup(ClusterSpec.Group.from(1), ClusterSpec.Group.from(1))
                .addEqualityGroup(ClusterSpec.Group.from(2))
                .addEqualityGroup(ClusterSpec.Group.from(3))
                .testEquals();
    }

    @Test
    public void testSatisfies() {
        var tests = Map.of(
                List.of(spec(ClusterSpec.Type.content, "id1"), spec(ClusterSpec.Type.content, "id2")), false,
                List.of(spec(ClusterSpec.Type.admin, "id1"), spec(ClusterSpec.Type.container, "id1")), false,
                List.of(spec(ClusterSpec.Type.admin, "id1"), spec(ClusterSpec.Type.content, "id1")), false,
                List.of(spec(ClusterSpec.Type.combined, "id1"), spec(ClusterSpec.Type.container, "id1")), false,
                List.of(spec(ClusterSpec.Type.combined, "id1"), spec(ClusterSpec.Type.content, "id1")), true,
                List.of(spec(ClusterSpec.Type.content, "id1"), spec(ClusterSpec.Type.content, "id1")), true
        );
        tests.forEach((specs, satisfies) -> {
            var s1 = specs.get(0);
            var s2 = specs.get(1);
            assertEquals(s1 + (satisfies ? " satisfies " : " does not satisfy ") + s2, satisfies, s1.satisfies(s2));
            assertEquals(s2 + (satisfies ? " satisfies " : " does not satisfy ") + s1, satisfies, s2.satisfies(s1));
        });
    }

    private static ClusterSpec spec(ClusterSpec.Type type, String id) {
        return ClusterSpec.from(type, ClusterSpec.Id.from(id), ClusterSpec.Group.from(1), Version.emptyVersion,
                                false, Optional.empty());
    }

}
