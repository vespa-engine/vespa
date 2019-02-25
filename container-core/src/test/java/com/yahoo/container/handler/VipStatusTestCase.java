// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler;

import static org.junit.Assert.*;

import com.yahoo.container.QrSearchersConfig;
import org.junit.Test;

/**
 * Smoke test that VipStatus has the right basic logic.
 *
 * @author steinar
 */
public class VipStatusTestCase {

    @Test
    public void testVipStatusWorksWithClusters() {
        var b = new QrSearchersConfig.Builder();
        var searchClusterB = new QrSearchersConfig.Searchcluster.Builder();
        searchClusterB.name("cluster1");
        searchClusterB.name("cluster2");
        searchClusterB.name("cluster3");
        b.searchcluster(searchClusterB);
        VipStatus v = new VipStatus(b.build());

        String cluster1 = "cluster1";
        String cluster2 = "cluster2";
        String cluster3 = "cluster3";

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
