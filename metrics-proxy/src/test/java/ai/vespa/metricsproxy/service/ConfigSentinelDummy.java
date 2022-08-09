// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.service;

/**
 * @author Eirik Nygaard
 */
public class ConfigSentinelDummy {
    private String serviceList =
            "docprocservice state=FINISHED mode=MANUAL pid=6555 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"docproc/cluster.x.indexing/0\"\n"
                    + "distributor state=RUNNING mode=AUTO pid=6548 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"storage/cluster.storage/distributor/0\"\n"
                    + "fleetcontroller state=RUNNING mode=AUTO pid=6543 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"storage/cluster.storage/fleetcontroller/0\"\n"
                    + "storagenode state=RUNNING mode=AUTO pid=6539 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"storage/cluster.storage/storage/0\"\n"
                    + "searchnode4 state=RUNNING mode=AUTO pid=6534 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"search/cluster.x/g0/c1/r1\"\n"
                    + "container2 state=RUNNING mode=AUTO pid=6521 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"container/default.1\"\n"
                    + "logserver state=RUNNING mode=AUTO pid=6518 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"admin/logserver\"\n"
                    + "logd state=RUNNING mode=AUTO pid=6517 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"hosts/dell-bl5s7.trondheim.corp.yahoo.com/logd\"\n"
                    + "searchnode2 state=RUNNING mode=AUTO pid=6527 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"search/cluster.x/g0/c0/r1\"\n"
                    + "clustercontroller2 state=RUNNING mode=AUTO pid=6523 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"search/cluster.x/rtx/1\"\n"
                    + "clustercontroller state=RUNNING mode=AUTO pid=6522 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"search/cluster.x/rtx/0\"\n"
                    + "slobrok state=RUNNING mode=AUTO pid=6519 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"client\"\n"
                    + "searchnode3 state=RUNNING mode=AUTO pid=6529 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"search/cluster.x/g0/c1/r0\"\n"
                    + "searchnode state=RUNNING mode=AUTO pid=6526 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"search/cluster.x/g0/c0/r0\"\n"
                    + "container state=RUNNING mode=AUTO pid=6520 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"container/default.0\"\n"
                    + "\n";


    public ConfigSentinelDummy() {
    }

    public ConfigSentinelDummy(String response) {
        serviceList = response;
    }

    public void reConfigure() {
        this.serviceList = "docprocservice state=RUNNING mode=AUTO pid=100 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"docproc/cluster.x.indexing/0\"\n"
                + "distributor state=RUNNING mode=AUTO pid=6548 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"storage/cluster.storage/distributor/0\"\n"
                + "fleetcontroller state=RUNNING mode=AUTO pid=6543 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"storage/cluster.storage/fleetcontroller/0\"\n"
                + "storagenode state=RUNNING mode=AUTO pid=6539 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"storage/cluster.storage/storage/0\"\n"
                + "searchnode4 state=RUNNING mode=AUTO pid=6534 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"search/cluster.x/g0/c1/r1\"\n"
                + "container2 state=RUNNING mode=AUTO pid=6521 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"container/default.1\"\n"
                + "logserver state=RUNNING mode=AUTO pid=6518 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"admin/logserver\"\n"
                + "logd state=RUNNING mode=AUTO pid=6517 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"hosts/dell-bl5s7.trondheim.corp.yahoo.com/logd\"\n"
                + "searchnode2 state=RUNNING mode=AUTO pid=6527 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"search/cluster.x/g0/c0/r1\"\n"
                + "clustercontroller2 state=RUNNING mode=AUTO pid=6523 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"search/cluster.x/rtx/1\"\n"
                + "clustercontroller state=RUNNING mode=AUTO pid=6522 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"search/cluster.x/rtx/0\"\n"
                + "slobrok state=RUNNING mode=AUTO pid=6519 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"client\"\n"
                + "searchnode3 state=RUNNING mode=AUTO pid=6529 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"search/cluster.x/g0/c1/r0\"\n"
                + "searchnode state=RUNNING mode=AUTO pid=6526 exitstatus=0 autostart=TRUE autorestart=TRUE id=\"search/cluster.x/g0/c0/r0\"\n"
                + "\n";
    }

    public String getServiceList() {
        return serviceList;
    }
}
