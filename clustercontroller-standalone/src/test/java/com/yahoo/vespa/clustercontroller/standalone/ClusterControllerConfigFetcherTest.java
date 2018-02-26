// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.standalone;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ClusterControllerConfigFetcherTest extends ClusterControllerTest {

    @Test
    public void testSimple() throws Exception {
        setFleetControllerConfigProperty();
        setSlobrokConfigProperty();
        addFleetControllerConfig(2, 1);
        addSlobrokConfig();
        addDistributionConfig();
        addZookeepersConfig();
        ClusterControllerConfigFetcher configFetcher = new ClusterControllerConfigFetcher();
        configFetcher.getOptions();
        configFetcher.updated(100);
        assertEquals(1, configFetcher.getGeneration());
        configFetcher.close();
    }

    @Test
    public void testInitialConfigFailure() throws Exception {
        setFleetControllerConfigProperty();
        setSlobrokConfigProperty();
        addFleetControllerConfig(2, 1);
        addSlobrokConfig();
        addDistributionConfig();
        addZookeepersConfig();
        try{
            ClusterControllerConfigFetcher configFetcher = new ClusterControllerConfigFetcher() {
                boolean configReady() {
                    return false;
                }
            };
            fail("Control should not reach here");
        } catch (IllegalStateException e) {
            assertEquals("Initial configuration failed.", e.getMessage());
        }
    }

    @Test
    public void testConfigUpdate() throws Exception {
        setFleetControllerConfigProperty();
        setSlobrokConfigProperty();
        addFleetControllerConfig(2, 1);
        addSlobrokConfig();
        addDistributionConfig();
        addZookeepersConfig();
        ClusterControllerConfigFetcher configFetcher = new ClusterControllerConfigFetcher() {
            boolean configUpdated(long millis) {
                return true;
            }
        };
        configFetcher.updated(1000);
    }

}
