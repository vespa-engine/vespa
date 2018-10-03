// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Smoke test that VipStatus has the right basic logic.
 *
 * @author steinar
 */
public class VipStatusTestCase {

    @Test
    public void testVipStatusWorksWithClusters() {
        ClustersStatus clustersStatus = new ClustersStatus();
        clustersStatus.setContainerHasClusters(true);
        VipStatus v = new VipStatus(clustersStatus);

        Object cluster1 = new Object();
        Object cluster2 = new Object();
        Object cluster3 = new Object();

        // initial state
        assertFalse(v.isInRotation());

        // one cluster becomes up
        v.addToRotation(cluster1);
        assertTrue(v.isInRotation());

        // all clusters down
        v.removeFromRotation(cluster1);
        v.removeFromRotation(cluster2);
        v.removeFromRotation(cluster3);
        assertFalse(v.isInRotation());
        // some clusters down
        v.addToRotation(cluster2);
        assertTrue(v.isInRotation());
        // all clusters up
        v.addToRotation(cluster1);
        v.addToRotation(cluster3);
        assertTrue(v.isInRotation());
        // and down again
        v.removeFromRotation(cluster1);
        v.removeFromRotation(cluster2);
        v.removeFromRotation(cluster3);
        assertFalse(v.isInRotation());
    }

}
