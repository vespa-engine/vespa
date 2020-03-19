// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.service;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

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
        VespaService qrserver = new VespaService("qrserver", "container/qrserver.0");

        services.add(searchnode4);
        services.add(qrserver);
        services.add(docproc);

        MockConfigSentinelClient client = new MockConfigSentinelClient(configsentinel);
        client.updateServiceStatuses(services);

        assertThat(qrserver.getPid(), is(6520));
        assertThat(qrserver.getState(), is("RUNNING"));
        assertThat(qrserver.isAlive(), is(true));
        assertThat(searchnode4.getPid(), is(6534));
        assertThat(searchnode4.getState(), is("RUNNING"));
        assertThat(searchnode4.isAlive(), is(true));

        assertThat(docproc.getPid(), is(-1));
        assertThat(docproc.getState(), is("FINISHED"));
        assertThat(docproc.isAlive(), is(false));


        configsentinel.reConfigure();

        client.ping(docproc);
        assertThat(docproc.getPid(), is(100));
        assertThat(docproc.getState(), is("RUNNING"));
        assertThat(docproc.isAlive(), is(true));

        //qrserver has yet not been checked
        assertThat(qrserver.isAlive(), is(true));

        client.updateServiceStatuses(services);

        assertThat(docproc.getPid(), is(100));
        assertThat(docproc.getState(), is("RUNNING"));
        assertThat(docproc.isAlive(), is(true));
        //qrserver is no longer running on this node - so should be false
        assertThat(qrserver.isAlive(), is(false));
    }

    @Test
    public void testElastic() throws Exception {
        String response = "container state=RUNNING mode=AUTO pid=14338 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"get/container.0\"\n" +
                "container-clustercontroller state=RUNNING mode=AUTO pid=25020 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"admin/cluster-controllers/0\"\n" +
                "distributor state=RUNNING mode=AUTO pid=25024 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"search/distributor/0\"\n" +
                "docprocservice state=RUNNING mode=AUTO pid=11973 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"docproc/cluster.search.indexing/0\"\n" +
                "logd state=RUNNING mode=AUTO pid=25016 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"hosts/vespa19.dev.gq1.yahoo.com/logd\"\n" +
                "logserver state=RUNNING mode=AUTO pid=25018 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"admin/logserver\"\n" +
                "metricsproxy state=RUNNING mode=AUTO pid=13107 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"hosts/vespa19.dev.gq1.yahoo.com/metricsproxy\"\n" +
                "searchnode state=RUNNING mode=AUTO pid=25023 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"search/search/cluster.search/0\"\n" +
                "slobrok state=RUNNING mode=AUTO pid=25019 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"admin/slobrok.0\"\n" +
                "topleveldispatch state=RUNNING mode=AUTO pid=25026 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"search/search/cluster.search/tlds/tld.0\"\n" +
                "\n";

        ConfigSentinelDummy configsentinel = new ConfigSentinelDummy(response);
        List<VespaService> services = new ArrayList<>();

        VespaService container = VespaService.create("container", "get/container.0", -1);

        VespaService containerClusterController =
                VespaService.create("container-clustercontroller", "get/container.0", -1);

        VespaService notPresent = VespaService.create("dummy","fake", -1);

        services.add(container);
        services.add(containerClusterController);
        services.add(notPresent);

        MockConfigSentinelClient client = new MockConfigSentinelClient(configsentinel);
        client.updateServiceStatuses(services);
        assertThat(container.isAlive(),is(true));
        assertThat(container.getPid(),is(14338));
        assertThat(container.getState(),is("RUNNING"));

        assertThat(containerClusterController.isAlive(),is(true));
        assertThat(containerClusterController.getPid(),is(25020));
        assertThat(containerClusterController.getState(),is("RUNNING"));
    }

}
