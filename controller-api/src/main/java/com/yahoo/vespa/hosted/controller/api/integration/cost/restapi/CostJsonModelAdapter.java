// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.cost.restapi;

import com.yahoo.config.provision.Flavor;
import com.yahoo.slime.Cursor;
import com.yahoo.vespa.hosted.controller.api.integration.cost.CostApplication;
import com.yahoo.vespa.hosted.controller.api.integration.cost.CostCluster;
import com.yahoo.vespa.hosted.controller.api.integration.cost.CostResources;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Serializing and de-serializing cost model
 *
 * @author smorgrav
 */
public class CostJsonModelAdapter {

    public static CostJsonModel.Application toJsonModel(CostApplication appCost) {
        CostJsonModel.Application app = new CostJsonModel.Application();
        app.zone = appCost.getZone().toString();
        app.tenant = appCost.getAppId().tenant().value();
        app.app = appCost.getAppId().application().value();
        app.tco = (int)appCost.getTco();
        app.utilization = appCost.getUtilization();
        app.waste = appCost.getWaste();
        app.cluster = new HashMap<>();
        Map<String, CostCluster> clusterMap = appCost.getCluster();
        for (String key : clusterMap.keySet()) {
            app.cluster.put(key, toJsonModel(clusterMap.get(key)));
        }

        return app;
    }

    public static void toSlime(CostApplication appCost, Cursor object) {
        object.setString("zone", appCost.getZone().toString());
        object.setString("tenant", appCost.getAppId().tenant().value());
        object.setString("app", appCost.getAppId().application().value() + "." + appCost.getAppId().instance().value());
        object.setLong("tco", (long)appCost.getTco());
        object.setDouble("utilization", appCost.getUtilization());
        object.setDouble("waste", appCost.getWaste());
        Cursor clustersObject = object.setObject("cluster");
        for (Map.Entry<String, CostCluster> clusterEntry : appCost.getCluster().entrySet())
            toSlime(clusterEntry.getValue(), clustersObject.setObject(clusterEntry.getKey()));
    }

    public static CostJsonModel.Cluster toJsonModel(CostCluster clusterCost) {
        CostJsonModel.Cluster cluster = new CostJsonModel.Cluster();
        cluster.count = clusterCost.getClusterInfo().getHostnames().size();
        cluster.resource = getResourceName(clusterCost.getResultUtilization());
        cluster.utilization = clusterCost.getResultUtilization().getMaxUtilization();
        cluster.tco = (int)clusterCost.getTco();
        cluster.flavor = clusterCost.getClusterInfo().getFlavor().toString();
        cluster.waste = (int)clusterCost.getWaste();
        cluster.type = clusterCost.getClusterInfo().getClusterType().name();
        cluster.util = new CostJsonModel.HardwareResources();
        cluster.util.cpu = clusterCost.getResultUtilization().getCpu();
        cluster.util.mem = clusterCost.getResultUtilization().getMemory();
        cluster.util.disk = clusterCost.getResultUtilization().getDisk();
        cluster.util.diskBusy = clusterCost.getResultUtilization().getDiskBusy();

        Flavor flavor = clusterCost.getClusterInfo().getFlavor();
        cluster.usage = new CostJsonModel.HardwareResources();
        cluster.usage.cpu = flavor.getMinCpuCores() * clusterCost.getSystemUtilization().getCpu();
        cluster.usage.mem = flavor.getMinMainMemoryAvailableGb() *  clusterCost.getSystemUtilization().getMemory();
        cluster.usage.disk = flavor.getMinDiskAvailableGb() * clusterCost.getSystemUtilization().getDisk();
        cluster.usage.diskBusy = clusterCost.getSystemUtilization().getDiskBusy();
        cluster.hostnames = new ArrayList<>(clusterCost.getClusterInfo().getHostnames());

        return cluster;
    }

    private static void toSlime(CostCluster clusterCost, Cursor object) {
        object.setLong("count", clusterCost.getClusterInfo().getHostnames().size());
        object.setString("resource", getResourceName(clusterCost.getResultUtilization()));
        object.setDouble("utilization", clusterCost.getResultUtilization().getMaxUtilization());
        object.setLong("tco", (int)clusterCost.getTco());
        object.setString("flavor", clusterCost.getClusterInfo().getClusterType().name());
        object.setLong("waste", (int)clusterCost.getWaste());
        object.setString("type", clusterCost.getClusterInfo().getClusterType().name());
        Cursor utilObject = object.setObject("util");
        utilObject.setDouble("cpu", clusterCost.getResultUtilization().getCpu());
        utilObject.setDouble("mem", clusterCost.getResultUtilization().getMemory());
        utilObject.setDouble("disk", clusterCost.getResultUtilization().getDisk());
        utilObject.setDouble("diskBusy", clusterCost.getResultUtilization().getDiskBusy());
        Cursor usageObject = object.setObject("usage");
        usageObject.setDouble("cpu", clusterCost.getSystemUtilization().getCpu() * 100);
        usageObject.setDouble("mem", clusterCost.getSystemUtilization().getMemory() * 100);
        usageObject.setDouble("disk", clusterCost.getSystemUtilization().getDisk() * 100);
        usageObject.setDouble("diskBusy", clusterCost.getSystemUtilization().getDiskBusy() * 100);
        Cursor hostnamesArray = object.setArray("hostnames");
        for (String hostname : clusterCost.getClusterInfo().getHostnames())
            hostnamesArray.addString(hostname);
    }

    private static String getResourceName(CostResources resources) {
        String name = "cpu";
        double max = resources.getMaxUtilization();

        if (resources.getMemory() == max) {
            name = "mem";
        } else if (resources.getDisk() == max) {
            name = "disk";
        } else if (resources.getDiskBusy() == max) {
            name = "diskbusy";
        }

        return name;
    }
}
