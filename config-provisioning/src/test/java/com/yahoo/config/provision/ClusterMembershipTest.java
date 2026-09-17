// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.yahoo.component.Vtag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author bratseth
 */
public class ClusterMembershipTest {

    @Test
    void testContainerServiceInstance() {
        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("id1")).vespaVersion("6.42").build();
        assertContainerService(ClusterMembership.from(cluster, 0, 3));
    }

    @Test
    void testSerializationWithOptionalParts() {
        {
            ClusterMembership instance = ClusterMembership.from("container/id1/4/37/exclusive/retired");
            ClusterMembership serialized = ClusterMembership.from(instance.stringValue());
            assertEquals(instance, serialized);
            assertTrue(instance.retired());
        }
        {
            ClusterMembership instance = ClusterMembership.from("container/id1/4/37/exclusive");
            ClusterMembership serialized = ClusterMembership.from(instance.stringValue());
            assertEquals(instance, serialized);
        }
        {
            ClusterMembership instance = ClusterMembership.from("container/id1/4/37/stateful");
            ClusterMembership serialized = ClusterMembership.from(instance.stringValue());
            assertEquals(instance, serialized);
        }
    }

    @Test
    void testServiceInstance() {
        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("id1")).vespaVersion("6.42").build();
        assertContentService(ClusterMembership.from(cluster, 0, 37));
    }

    @Test
    void testServiceInstanceWithGroupFromString() {
        assertContentServiceWithGroup(ClusterMembership.from("content/id1/4/37"));
    }

    @Test
    void testServiceInstanceWithRetire() {
        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("id1")).vespaVersion("6.42").build();
        assertContentServiceWithRetire(ClusterMembership.retiredFrom(cluster, 0, 37));
    }

    @Test
    void testServiceInstanceWithGroupAndRetireFromString() {
        assertContentServiceWithGroupAndRetire(ClusterMembership.from("content/id1/4/37/retired"));
    }

    @Test
    void testProfilePreservedOnClusterSpec() {
        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("id1"))
                                         .vespaVersion("6.42")
                                         .profile("large-storage")
                                         .build();
        ClusterMembership membership = ClusterMembership.from(cluster, 0, 37);
        assertEquals("content/id1/0/37/stateful", membership.stringValue());
    }

    private void assertContainerService(ClusterMembership instance) {
        assertEquals(ClusterSpec.Type.container, instance.type());
        assertEquals("id1", instance.id().value());
        assertEquals(0, instance.group());
        assertEquals(3, instance.index());
        assertEquals("container/id1/0/3", instance.stringValue());
        // Legacy form:
        assertEquals(instance, ClusterMembership.from("container/id1/3"));
    }

    private void assertContentService(ClusterMembership instance) {
        assertEquals(ClusterSpec.Type.content, instance.type());
        assertEquals("id1", instance.id().value());
        assertEquals(0, instance.group());
        assertEquals(37, instance.index());
        assertFalse(instance.retired());
        assertEquals("content/id1/0/37/stateful", instance.stringValue());
    }

    private void assertContentServiceWithGroup(ClusterMembership instance) {
        assertEquals(ClusterSpec.Type.content, instance.type());
        assertEquals("id1", instance.id().value());
        assertEquals(4, instance.group());
        assertEquals(37, instance.index());
        assertFalse(instance.retired());
        assertEquals("content/id1/4/37/stateful", instance.stringValue());
    }

    /** Serializing a spec without a group assigned works, but not deserialization */
    private void assertContentServiceWithRetire(ClusterMembership instance) {
        assertEquals(ClusterSpec.Type.content, instance.type());
        assertEquals("id1", instance.id().value());
        assertEquals(37, instance.index());
        assertTrue(instance.retired());
        assertEquals("content/id1/0/37/retired/stateful", instance.stringValue());
        // Legacy form:
        assertEquals(instance, ClusterMembership.from("content/id1/37/retired"));
    }

    private void assertContentServiceWithGroupAndRetire(ClusterMembership instance) {
        assertEquals(ClusterSpec.Type.content, instance.type());
        assertEquals("id1", instance.id().value());
        assertEquals(4, instance.group());
        assertEquals(37, instance.index());
        assertTrue(instance.retired());
        assertEquals("content/id1/4/37/retired/stateful", instance.stringValue());
    }

}
