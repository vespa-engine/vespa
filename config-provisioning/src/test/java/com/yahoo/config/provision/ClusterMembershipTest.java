// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.yahoo.component.*;
import com.yahoo.component.Version;
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
        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("id1"), Version.fromString("6.42"), false);
        assertContainerService(ClusterMembership.from(cluster, 3));
    }

    @Test
    public void testServiceInstance() {
        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("id1"), Version.fromString("6.42"), false);
        assertContentService(ClusterMembership.from(cluster, 37));
    }

    @Test
    public void testServiceInstanceWithGroup() {
        ClusterSpec cluster = ClusterSpec.from(ClusterSpec.Type.content, ClusterSpec.Id.from("id1"),
                                               ClusterSpec.Group.from(4), Version.fromString("6.42"), false);
        assertContentServiceWithGroup(ClusterMembership.from(cluster, 37));
    }

    @Test
    public void testServiceInstanceWithGroupFromString() {
        assertContentServiceWithGroup(ClusterMembership.from("content/id1/4/37", Vtag.currentVersion));
    }

    @Test
    public void testServiceInstanceWithRetire() {
        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("id1"), Version.fromString("6.42"), false);
        assertContentServiceWithRetire(ClusterMembership.retiredFrom(cluster, 37));
    }

    @Test
    public void testServiceInstanceWithGroupAndRetire() {
        ClusterSpec cluster = ClusterSpec.from(ClusterSpec.Type.content, ClusterSpec.Id.from("id1"),
                                               ClusterSpec.Group.from(4), Version.fromString("6.42"), false);
        assertContentServiceWithGroupAndRetire(ClusterMembership.retiredFrom(cluster, 37));
    }

    @Test
    public void testServiceInstanceWithGroupAndRetireFromString() {
        assertContentServiceWithGroupAndRetire(ClusterMembership.from("content/id1/4/37/retired", Vtag.currentVersion));
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
        assertEquals("content/id1/37", instance.stringValue());
    }

    private void assertContentServiceWithGroup(ClusterMembership instance) {
        assertEquals(ClusterSpec.Type.content, instance.cluster().type());
        assertEquals("id1", instance.cluster().id().value());
        assertEquals(4, instance.cluster().group().get().index());
        assertEquals(37, instance.index());
        assertFalse(instance.retired());
        assertEquals("content/id1/4/37", instance.stringValue());
    }

    /** Serializing a spec without a group assigned works, but not deserialization */
    private void assertContentServiceWithRetire(ClusterMembership instance) {
        assertEquals(ClusterSpec.Type.content, instance.cluster().type());
        assertEquals("id1", instance.cluster().id().value());
        assertEquals(37, instance.index());
        assertTrue(instance.retired());
        assertEquals("content/id1/37/retired", instance.stringValue());
    }

    private void assertContentServiceWithGroupAndRetire(ClusterMembership instance) {
        assertEquals(ClusterSpec.Type.content, instance.cluster().type());
        assertEquals("id1", instance.cluster().id().value());
        assertEquals(4, instance.cluster().group().get().index());
        assertEquals(37, instance.index());
        assertTrue(instance.retired());
        assertEquals("content/id1/4/37/retired", instance.stringValue());
    }

}
