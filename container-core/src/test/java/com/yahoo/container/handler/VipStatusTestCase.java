// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler;

import static org.junit.Assert.*;

import com.yahoo.container.QrSearchersConfig;
import com.yahoo.container.jdisc.state.StateMonitor;
import com.yahoo.jdisc.core.SystemTimer;
import org.junit.Test;

/**
 * Smoke test that VipStatus has the right basic logic.
 *
 * @author steinar
 */
public class VipStatusTestCase {
    private static final String [] clusters = {"cluster1", "cluster2", "cluster3"};

    private static QrSearchersConfig getSearchersCfg() {
        var b = new QrSearchersConfig.Builder();
        var searchClusterB = new QrSearchersConfig.Searchcluster.Builder();
        for (String cluster : clusters) {
            searchClusterB.name(cluster);
        }
        b.searchcluster(searchClusterB);
        return b.build();
    }
    private static VipStatus getVipStatus(StateMonitor.Status startState) {
        return new VipStatus(getSearchersCfg(), new ClustersStatus(), new StateMonitor(1000, startState, new SystemTimer(), runnable -> {
            Thread thread = new Thread(runnable, "StateMonitor");
            thread.setDaemon(true);
            return thread;
        }));
    }

    private static void removeAll(VipStatus v) {
        for (String s : clusters) {
            v.removeFromRotation(s);
        }
    }
    private static void addAll(VipStatus v) {
        for (String s : clusters) {
            v.addToRotation(s);
        }
    }

    private static void verifyUpOrDown(StateMonitor.Status status) {
        VipStatus v = getVipStatus(status);
        removeAll(v);
        // initial state
        assertFalse(v.isInRotation());
        v.addToRotation(clusters[0]);
        assertFalse(v.isInRotation());
        v.addToRotation(clusters[1]);
        assertFalse(v.isInRotation());
        v.addToRotation(clusters[2]);
        assertTrue(v.isInRotation());
    }

    @Test
    public void testInitializingOrDownRequireAllUp() {
        verifyUpOrDown(StateMonitor.Status.initializing);
        verifyUpOrDown(StateMonitor.Status.down);
    }

    @Test
    public void testUpRequireAllDown() {
        VipStatus v = getVipStatus(StateMonitor.Status.initializing);
        assertFalse(v.isInRotation());
        addAll(v);
        assertTrue(v.isInRotation());

        v.removeFromRotation(clusters[0]);
        assertTrue(v.isInRotation());
        v.removeFromRotation(clusters[1]);
        assertTrue(v.isInRotation());
        v.removeFromRotation(clusters[2]);
        assertFalse(v.isInRotation());  // All down
        v.addToRotation(clusters[1]);
        assertFalse(v.isInRotation());
        v.addToRotation(clusters[0]);
        v.addToRotation(clusters[2]);
        assertTrue(v.isInRotation());   // All up
        v.removeFromRotation(clusters[0]);
        v.removeFromRotation(clusters[2]);
        assertTrue(v.isInRotation());
        v.addToRotation(clusters[0]);
        v.addToRotation(clusters[2]);
        assertTrue(v.isInRotation());
    }

}
