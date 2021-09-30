// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.yahoo.component.Vtag;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class ClusterMembershipTest {

    @Test
    public void testContainerServiceInstance() {
        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("id1")).vespaVersion("6.42").build();
        assertContainerService(ClusterMembership.from(cluster, 3));
    }

    @Test
    public void testSerializationWithOptionalParts() {
        {
            ClusterMembership instance = ClusterMembership.from("container/id1/4/37/exclusive/retired", Vtag.currentVersion, Optional.empty());
            ClusterMembership serialized = ClusterMembership.from(instance.stringValue(), Vtag.currentVersion, Optional.empty());
            assertFalse(serialized.cluster().isStateful());
            assertEquals(instance, serialized);
            assertTrue(instance.retired());
            assertTrue(instance.cluster().isExclusive());
        }
        {
            ClusterMembership instance = ClusterMembership.from("container/id1/4/37/exclusive", Vtag.currentVersion, Optional.empty());
            ClusterMembership serialized = ClusterMembership.from(instance.stringValue(), Vtag.currentVersion, Optional.empty());
            assertFalse(serialized.cluster().isStateful());
            assertEquals(instance, serialized);
            assertTrue(instance.cluster().isExclusive());
        }
        {
            Optional<DockerImage> dockerImageRepo = Optional.of(DockerImage.fromString("docker.foo.com:4443/vespa/bar"));
            ClusterMembership instance = ClusterMembership.from("combined/id1/4/37/exclusive/stateful/containerId1", Vtag.currentVersion, dockerImageRepo);
            ClusterMembership serialized = ClusterMembership.from(instance.stringValue(), Vtag.currentVersion, dockerImageRepo);
            assertTrue(serialized.cluster().isStateful());
            assertEquals(instance, serialized);
            assertEquals(ClusterSpec.Id.from("containerId1"), instance.cluster().combinedId().get());
            assertEquals(dockerImageRepo.get(), instance.cluster().dockerImageRepo().get());
        }
        {
            ClusterMembership instance = ClusterMembership.from("container/id1/4/37/stateful", Vtag.currentVersion, Optional.empty());
            ClusterMembership serialized = ClusterMembership.from(instance.stringValue(), Vtag.currentVersion, Optional.empty());
            assertEquals(instance, serialized);
            assertTrue(instance.cluster().isStateful());
        }
    }

    @Test
    public void testServiceInstance() {
        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("id1")).vespaVersion("6.42").build();
        assertContentService(ClusterMembership.from(cluster, 37));
    }

    @Test
    public void testServiceInstanceWithGroup() {
        ClusterSpec cluster = ClusterSpec.specification(ClusterSpec.Type.content, ClusterSpec.Id.from("id1"))
                .group(ClusterSpec.Group.from(4))
                .vespaVersion("6.42")
                .build();
        assertContentServiceWithGroup(ClusterMembership.from(cluster, 37));
    }

    @Test
    public void testServiceInstanceWithGroupFromString() {
        assertContentServiceWithGroup(ClusterMembership.from("content/id1/4/37/stateful", Vtag.currentVersion, Optional.empty()));
    }

    @Test
    public void testServiceInstanceWithRetire() {
        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("id1")).vespaVersion("6.42").build();
        assertContentServiceWithRetire(ClusterMembership.retiredFrom(cluster, 37));
    }

    @Test
    public void testServiceInstanceWithGroupAndRetire() {
        ClusterSpec cluster = ClusterSpec.specification(ClusterSpec.Type.content, ClusterSpec.Id.from("id1"))
                .group(ClusterSpec.Group.from(4))
                .vespaVersion("6.42")
                .build();
        assertContentServiceWithGroupAndRetire(ClusterMembership.retiredFrom(cluster, 37));
    }

    @Test
    public void testServiceInstanceWithGroupAndRetireFromString() {
        assertContentServiceWithGroupAndRetire(ClusterMembership.from("content/id1/4/37/retired/stateful", Vtag.currentVersion, Optional.empty()));
    }

    private void assertContainerService(ClusterMembership instance) {
        assertEquals(ClusterSpec.Type.container, instance.cluster().type());
        assertEquals("id1", instance.cluster().id().value());
        assertFalse(instance.cluster().group().isPresent());
        assertEquals(3, instance.index());
        assertEquals("container/id1/3", instance.stringValue());
    }

    private void assertContentService(ClusterMembership instance) {
        assertEquals(ClusterSpec.Type.content, instance.cluster().type());
        assertEquals("id1", instance.cluster().id().value());
        assertFalse(instance.cluster().group().isPresent());
        assertEquals(37, instance.index());
        assertFalse(instance.retired());
        assertEquals("content/id1/37/stateful", instance.stringValue());
    }

    private void assertContentServiceWithGroup(ClusterMembership instance) {
        assertEquals(ClusterSpec.Type.content, instance.cluster().type());
        assertEquals("id1", instance.cluster().id().value());
        assertEquals(4, instance.cluster().group().get().index());
        assertEquals(37, instance.index());
        assertFalse(instance.retired());
        assertEquals("content/id1/4/37/stateful", instance.stringValue());
    }

    /** Serializing a spec without a group assigned works, but not deserialization */
    private void assertContentServiceWithRetire(ClusterMembership instance) {
        assertEquals(ClusterSpec.Type.content, instance.cluster().type());
        assertEquals("id1", instance.cluster().id().value());
        assertEquals(37, instance.index());
        assertTrue(instance.retired());
        assertEquals("content/id1/37/retired/stateful", instance.stringValue());
    }

    private void assertContentServiceWithGroupAndRetire(ClusterMembership instance) {
        assertEquals(ClusterSpec.Type.content, instance.cluster().type());
        assertEquals("id1", instance.cluster().id().value());
        assertEquals(4, instance.cluster().group().get().index());
        assertEquals(37, instance.index());
        assertTrue(instance.retired());
        assertEquals("content/id1/4/37/retired/stateful", instance.stringValue());
    }

}
