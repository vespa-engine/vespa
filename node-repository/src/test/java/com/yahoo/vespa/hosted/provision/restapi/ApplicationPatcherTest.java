// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.hosted.provision.NodeRepositoryTester;
import com.yahoo.vespa.hosted.provision.applications.Application;
import com.yahoo.vespa.hosted.provision.applications.BcpGroupInfo;
import com.yahoo.vespa.hosted.provision.applications.Cluster;
import org.junit.Test;

import java.io.ByteArrayInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class ApplicationPatcherTest {

    @Test
    public void testPatching() {
        NodeRepositoryTester tester = new NodeRepositoryTester();
        Application application = Application.empty(ApplicationId.from("t1", "a1", "i1"));
        tester.nodeRepository().applications().put(application, tester.nodeRepository().applications().lock(application.id()));
        String patch = "{ \"currentReadShare\" :0.4, \"maxReadShare\": 1.0 }";
        ApplicationPatcher patcher = new ApplicationPatcher(new ByteArrayInputStream(patch.getBytes()),
                                                            application.id(),
                                                            tester.nodeRepository());
        Application patched = patcher.apply();
        assertEquals(0.4, patched.status().currentReadShare(), 0.0000001);
        assertEquals(1.0, patched.status().maxReadShare(), 0.0000001);
        patcher.close();
    }

    @Test
    public void testPatchingWithBcpGroupInfo() {
        var c1 = ClusterSpec.Id.from("c1");
        var c2 = ClusterSpec.Id.from("c2");
        var capacity = Capacity.from(new ClusterResources(10, 1, new NodeResources(1.0, 10.0, 100.0, 3.0)));
        NodeRepositoryTester tester = new NodeRepositoryTester();
        Application application = Application.empty(ApplicationId.from("t1", "a1", "i1"));
        application = application.with(Cluster.create(c1, false, capacity));
        application = application.with(Cluster.create(c2, false, capacity));
        tester.nodeRepository().applications().put(application, tester.nodeRepository().applications().lock(application.id()));

        String patch = """
        {
          "currentReadShare": 0.4,
          "maxReadShare": 1.0,
          "clusters": {
            "c1": {
              "bcpGroupInfo": {
                "queryRate": 0.1,
                "growthRateHeadroom": 0.2,
                "cpuCostPerQuery": 0.3
              }
            },
            "c2": {
              "bcpGroupInfo": {
                "queryRate": 1,
                "growthRateHeadroom": 2,
                "cpuCostPerQuery": 3
              }
            },
            "ignored": {
              "bcpGroupInfo": {
                "queryRate": 1,
                "growthRateHeadroom": 2,
                "cpuCostPerQuery": 3
              }
            }
          }
        }
        """;

        ApplicationPatcher patcher = new ApplicationPatcher(new ByteArrayInputStream(patch.getBytes()),
                                                            application.id(),
                                                            tester.nodeRepository());
        Application patched = patcher.apply();
        assertEquals(0.4, patched.status().currentReadShare(), 0.0000001);
        assertEquals(1.0, patched.status().maxReadShare(), 0.0000001);
        assertEquals(new BcpGroupInfo(0.1, 0.2, 0.3),
                     patched.cluster(c1).get().bcpGroupInfo());
        assertEquals(new BcpGroupInfo(1.0, 2.0, 3.0),
                     patched.cluster(c2).get().bcpGroupInfo());
        assertTrue(patched.cluster(ClusterSpec.Id.from("ignored")).isEmpty());
        patcher.close();
    }

}
