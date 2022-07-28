// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ClusterSpec.Group;
import com.yahoo.config.provision.ClusterSpec.Id;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Ulf Lilleengen
 */
public class ClusterSpecTest {

    @Test
    void testIdEquals() {
        assertEquals(Set.of(Id.from("id1"), Id.from("id2"), Id.from("id3")),
                               new HashSet<>(List.of(Id.from("id1"), Id.from("id1"), Id.from("id2"), Id.from("id3"))));
    }

    @Test
    void testGroupEquals() {
        assertEquals(Set.of(Group.from(1), Group.from(2), Group.from(3)),
                               new HashSet<>(List.of(Group.from(1), Group.from(1), Group.from(2), Group.from(3))));
    }

    @Test
    void testSatisfies() {
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
            assertEquals(satisfies, s1.satisfies(s2), s1 + (satisfies ? " satisfies " : " does not satisfy ") + s2);
            assertEquals(satisfies, s2.satisfies(s1), s2 + (satisfies ? " satisfies " : " does not satisfy ") + s1);
        });
    }

    private static ClusterSpec spec(ClusterSpec.Type type, String id) {
        ClusterSpec.Builder builder = ClusterSpec.specification(type, ClusterSpec.Id.from(id))
                                                 .group(ClusterSpec.Group.from(1))
                                                 .vespaVersion(Version.emptyVersion);
        if (type == ClusterSpec.Type.combined) {
            builder = builder.combinedId(Optional.of(ClusterSpec.Id.from("combined")));
        }
        return builder.build();
    }

}
