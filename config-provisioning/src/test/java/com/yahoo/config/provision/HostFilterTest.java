// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.yahoo.component.Vtag;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class HostFilterTest {

    @Test
    public void testSingleConditionFilter() {
        HostFilter all = HostFilter.all();
        HostFilter hostname = HostFilter.hostname("host1");
        HostFilter type = HostFilter.clusterType(ClusterSpec.Type.content);
        HostFilter id = HostFilter.clusterId(ClusterSpec.Id.from("type1"));

        assertTrue(      all.matches("anyhost", "flavor", membership("container/anytype/0/0")));
        assertFalse(hostname.matches("anyhost", "flavor", membership("container/anytype/0/0")));
        assertFalse(type.matches("anyhost", "flavor", membership("container/anytype/0/0")));
        assertFalse(id.matches("anyhost", "flavor", membership("container/anytype/0/0")));

        assertTrue(      all.matches("anyhost", "flavor", membership("content/anytype/0/0")));
        assertFalse(hostname.matches("anyhost", "flavor", membership("content/anytype/0/0")));
        assertTrue(     type.matches("anyhost", "flavor", membership("content/anytype/0/0")));
        assertFalse(      id.matches("anyhost", "flavor", membership("content/anytype/0/0")));

        assertTrue(      all.matches("host1", "flavor", membership("content/anytype/0/0")));
        assertTrue( hostname.matches("host1", "flavor", membership("content/anytype/0/0")));
        assertTrue(     type.matches("host1", "flavor", membership("content/anytype/0/0")));
        assertFalse(      id.matches("host1", "flavor", membership("content/anytype/0/0")));

        assertTrue(      all.matches("host1", "flavor", membership("content/type1/0/0")));
        assertTrue( hostname.matches("host1", "flavor", membership("content/type1/0/0")));
        assertTrue(     type.matches("host1", "flavor", membership("content/type1/0/0")));
        assertTrue(       id.matches("host1", "flavor", membership("content/type1/0/0")));
    }

    @Test
    public void testMultiConditionFilter() {
        HostFilter typeAndId = HostFilter.from(Collections.emptyList(),
                                               Collections.emptyList(),
                                               Collections.singletonList(ClusterSpec.Type.content),
                                               Collections.singletonList(ClusterSpec.Id.from("type1")));

        assertFalse(typeAndId.matches("anyhost", "flavor", membership("content/anyType/0/0")));
        assertFalse(typeAndId.matches("anyhost", "flavor", membership("container/type1/0/0")));
        assertTrue(typeAndId.matches("anyhost", "flavor", membership("content/type1/0/0")));
    }

    @Test
    public void testMultiConditionFilterFromStrings() {
        HostFilter typeAndId = HostFilter.from("host1  host2, host3,host4", "  , ,flavor", null, "type1  ");

        assertFalse(typeAndId.matches("anotherhost", "flavor", membership("content/type1/0/0")));
        assertTrue(typeAndId.matches("host1", "flavor", membership("content/type1/0/0")));
        assertTrue(typeAndId.matches("host2", "flavor", membership("content/type1/0/0")));
        assertTrue(typeAndId.matches("host3", "flavor", membership("content/type1/0/0")));
        assertTrue(typeAndId.matches("host4", "flavor", membership("content/type1/0/0")));
        assertFalse(typeAndId.matches("host1", "flavor", membership("content/type2/0/0")));
        assertFalse(typeAndId.matches("host4", "differentflavor", membership("content/type1/0/0")));
    }

    private Optional<ClusterMembership> membership(String membershipString) {
        return Optional.of(ClusterMembership.from(membershipString, Vtag.currentVersion));
    }

}
