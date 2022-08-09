// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.service;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

/**
 * @author Unknown
 */
public class ConfigSentinelClientTest {

    @Test
    public void testConfigSentinelClient() {
        ConfigSentinelDummy configsentinel = new ConfigSentinelDummy();
        List<VespaService> services = new ArrayList<>();
        VespaService docproc = new VespaService("docprocservice", "docproc/cluster.x.indexing/0");
        VespaService searchnode4 = new VespaService("searchnode4", "search/cluster.x/g0/c1/r1");
        VespaService container = new VespaService("container", "container/default.0");

        services.add(searchnode4);
        services.add(container);
        services.add(docproc);

        try (MockConfigSentinelClient client = new MockConfigSentinelClient(configsentinel)) {
            client.updateServiceStatuses(services);

            assertEquals(6520, container.getPid());
            assertEquals("RUNNING", container.getState());
            assertTrue(container.isAlive());
            assertEquals(6534, searchnode4.getPid());
            assertEquals("RUNNING", searchnode4.getState());
            assertTrue(searchnode4.isAlive());

            assertEquals(-1, docproc.getPid());
            assertEquals("FINISHED", docproc.getState());
            assertFalse(docproc.isAlive());


            configsentinel.reConfigure();

            client.ping(docproc);
            assertEquals(100, docproc.getPid());
            assertEquals("RUNNING", docproc.getState());
            assertTrue(docproc.isAlive());

            // container has yet not been checked
            assertTrue(container.isAlive());

            client.updateServiceStatuses(services);

            assertEquals(100, docproc.getPid());
            assertEquals("RUNNING", docproc.getState());
            assertTrue(docproc.isAlive());
            // container is no longer running on this node - so should be false
            assertFalse(container.isAlive());
        }
    }

    @Test
    public void testElastic() {
        String response = "container state=RUNNING mode=AUTO pid=14338 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"get/container.0\"\n" +
                "container-clustercontroller state=RUNNING mode=AUTO pid=25020 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"admin/cluster-controllers/0\"\n" +
                "distributor state=RUNNING mode=AUTO pid=25024 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"search/distributor/0\"\n" +
                "docprocservice state=RUNNING mode=AUTO pid=11973 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"docproc/cluster.search.indexing/0\"\n" +
                "logd state=RUNNING mode=AUTO pid=25016 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"hosts/vespa19.dev.gq1.yahoo.com/logd\"\n" +
                "logserver state=RUNNING mode=AUTO pid=25018 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"admin/logserver\"\n" +
                "metricsproxy state=RUNNING mode=AUTO pid=13107 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"hosts/vespa19.dev.gq1.yahoo.com/metricsproxy\"\n" +
                "searchnode state=RUNNING mode=AUTO pid=25023 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"search/search/cluster.search/0\"\n" +
                "slobrok state=RUNNING mode=AUTO pid=25019 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"client\"\n" +
                "\n";

        ConfigSentinelDummy configsentinel = new ConfigSentinelDummy(response);
        List<VespaService> services = new ArrayList<>();

        VespaService container = VespaService.create("container", "get/container.0", -1);

        VespaService containerClusterController = VespaService.create("container-clustercontroller", "get/container.0", -1);

        VespaService notPresent = VespaService.create("dummy","fake", -1);

        services.add(container);
        services.add(containerClusterController);
        services.add(notPresent);

        try (MockConfigSentinelClient client = new MockConfigSentinelClient(configsentinel)) {
            client.updateServiceStatuses(services);
            assertTrue(container.isAlive());
            assertEquals(14338, container.getPid());
            assertEquals("RUNNING", container.getState());

            assertTrue(containerClusterController.isAlive());
            assertEquals(25020, containerClusterController.getPid());
            assertEquals("RUNNING", containerClusterController.getState());
        }
    }

}
