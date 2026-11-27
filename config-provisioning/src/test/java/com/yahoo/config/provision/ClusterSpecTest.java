// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ClusterSpec.Id;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void testSatisfies() {
        var tests = Map.of(
                               List.of(spec(ClusterSpec.Type.content, "id1"), spec(ClusterSpec.Type.content, "id2")), false,
                               List.of(spec(ClusterSpec.Type.admin, "id1"), spec(ClusterSpec.Type.container, "id1")), false,
                               List.of(spec(ClusterSpec.Type.admin, "id1"), spec(ClusterSpec.Type.content, "id1")), false,
                               List.of(spec(ClusterSpec.Type.content, "id1"), spec(ClusterSpec.Type.content, "id1")), true
        );
        tests.forEach((specs, satisfies) -> {
            var s1 = specs.get(0);
            var s2 = specs.get(1);
            assertEquals(satisfies, s1.satisfies(s2), s1 + (satisfies ? " satisfies " : " does not satisfy ") + s2);
            assertEquals(satisfies, s2.satisfies(s1), s2 + (satisfies ? " satisfies " : " does not satisfy ") + s1);
        });
    }

    private static final ClusterResources resources = new ClusterResources(2, 1, NodeResources.unspecified());

    private static final CloudAccount account = CloudAccount.from("012345678912");
    private static final CloudResourceTags tags = CloudResourceTags.from(Map.of("team", "a"));
    private static final ClusterInfo info = new ClusterInfo.Builder().bcpDeadline(Duration.ofMinutes(30))
                                                                     .hostTTL(Duration.ofHours(1)).build();

    private static final CloudAccount otherAccount = CloudAccount.from("123456789012");
    private static final CloudResourceTags otherTags = CloudResourceTags.from(Map.of("team", "b"));
    private static final ClusterInfo otherInfo = new ClusterInfo.Builder().bcpDeadline(Duration.ofMinutes(60)).build();

    @Test
    @SuppressWarnings("deprecation")
    void valuesSetOnlyInCapacityAreCopiedToClusterSpec() {
        var capacity = Capacity.from(resources, resources, IntRange.empty(), 1.0, false, true, account, tags, info);
        var spec = ClusterSpec.builder(ClusterSpec.Type.container, Id.from("c"), capacity, Version.emptyVersion).build();
        assertEquals(account, spec.cloudAccount());
        assertEquals(tags, spec.cloudResourceTags());
        assertEquals(info, capacity.clusterInfo());
    }

    @Test
    @SuppressWarnings("deprecation")
    void builderSettersChangeValuesOfExistingSpec() {
        var spec = ClusterSpec.builder(ClusterSpec.Type.container, Id.from("c"),
                                       Capacity.from(resources, resources, IntRange.empty(), 1.0, false, true, account, tags, info),
                                       Version.emptyVersion).build();
        var changed = spec.builder().cloudAccount(otherAccount).cloudResourceTags(otherTags).clusterInfo(otherInfo).build();
        assertEquals(otherAccount, changed.cloudAccount());
        assertEquals(otherTags, changed.cloudResourceTags());
        assertEquals(otherInfo, changed.clusterInfo());
    }

    @Test
    void builderSettersRejectNull() {
        var builder = ClusterSpec.builder(ClusterSpec.Type.container, Id.from("c"), Capacity.unspecified(), Version.emptyVersion);
        assertEquals("cloudAccount cannot be null",
                     assertThrows(NullPointerException.class, () -> builder.cloudAccount(null)).getMessage());
        assertEquals("cloudResourceTags cannot be null",
                     assertThrows(NullPointerException.class, () -> builder.cloudResourceTags(null)).getMessage());
        assertEquals("clusterInfo cannot be null",
                     assertThrows(NullPointerException.class, () -> builder.clusterInfo(null)).getMessage());
    }

    private static ClusterSpec spec(ClusterSpec.Type type, String id) {
        return ClusterSpec.builder(type, ClusterSpec.Id.from(id), Capacity.unspecified(), Version.emptyVersion).build();
    }

}
