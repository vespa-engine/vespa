// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import org.junit.Test;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import java.util.Optional;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class ClusterMembershipTest {

    @Test
    public void testContainerServiceInstance() {
        ClusterSpec cluster = ClusterSpec.from(ClusterSpec.Type.container, ClusterSpec.Id.from("id1"), Optional.empty());
        assertContainerService(ClusterMembership.from(cluster, 3));
    }

    @Test
    public void testContainerServiceInstanceFromString() {
        assertContainerService(ClusterMembership.from("container/id1/3", Optional.empty()));
    }

    @Test
    public void testServiceInstance() {
        ClusterSpec cluster = ClusterSpec.from(ClusterSpec.Type.content, ClusterSpec.Id.from("id1"), Optional.empty());
        assertContentService(ClusterMembership.from(cluster, 37));
    }

    @Test
    public void testServiceInstanceFromString() {
        assertContentService(ClusterMembership.from("content/id1/37", Optional.empty()));
    }

    @Test
    public void testServiceInstanceWithGroup() {
        ClusterSpec cluster = ClusterSpec.from(ClusterSpec.Type.content, ClusterSpec.Id.from("id1"),
                                               Optional.of(ClusterSpec.Group.from("gr4")));
        assertContentServiceWithGroup(ClusterMembership.from(cluster, 37));
    }

    @Test
    public void testServiceInstanceWithGroupFromString() {
        assertContentServiceWithGroup(ClusterMembership.from("content/id1/gr4/37", Optional.empty()));
    }

    @Test
    public void testServiceInstanceWithRetire() {
        ClusterSpec cluster = ClusterSpec.from(ClusterSpec.Type.content, ClusterSpec.Id.from("id1"), Optional.empty());
        assertContentServiceWithRetire(ClusterMembership.retiredFrom(cluster, 37));
    }

    @Test
    public void testServiceInstanceWithRetireFromString() {
        assertContentServiceWithRetire(ClusterMembership.from("content/id1/37/retired", Optional.empty()));
    }

    @Test
    public void testServiceInstanceWithGroupAndRetire() {
        ClusterSpec cluster = ClusterSpec.from(ClusterSpec.Type.content, ClusterSpec.Id.from("id1"),
                Optional.of(ClusterSpec.Group.from("gr4")));
        assertContentServiceWithGroupAndRetire(ClusterMembership.retiredFrom(cluster, 37));
    }

    @Test
    public void testServiceInstanceWithGroupAndRetireFromString() {
        assertContentServiceWithGroupAndRetire(ClusterMembership.from("content/id1/gr4/37/retired", Optional.empty()));
    }

    private void assertContainerService(ClusterMembership instance) {
        assertEquals(ClusterSpec.Type.container, instance.cluster().type());
        assertEquals("id1", instance.cluster().id().value());
        assertEquals(Optional.<ClusterSpec.Group>empty(), instance.cluster().group());
        assertEquals(3, instance.index());
        assertEquals("container/id1/3", instance.stringValue());
    }

    private void assertContentService(ClusterMembership instance) {
        assertEquals(ClusterSpec.Type.content, instance.cluster().type());
        assertEquals("id1", instance.cluster().id().value());
        assertFalse("gr4", instance.cluster().group().isPresent());
        assertEquals(37, instance.index());
        assertFalse(instance.retired());
        assertEquals("content/id1/37", instance.stringValue());
    }

    private void assertContentServiceWithGroup(ClusterMembership instance) {
        assertEquals(ClusterSpec.Type.content, instance.cluster().type());
        assertEquals("id1", instance.cluster().id().value());
        assertEquals("gr4", instance.cluster().group().get().value());
        assertEquals(37, instance.index());
        assertFalse(instance.retired());
        assertEquals("content/id1/gr4/37", instance.stringValue());
    }

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
        assertEquals("gr4", instance.cluster().group().get().value());
        assertEquals(37, instance.index());
        assertTrue(instance.retired());
        assertEquals("content/id1/gr4/37/retired", instance.stringValue());
    }

}
