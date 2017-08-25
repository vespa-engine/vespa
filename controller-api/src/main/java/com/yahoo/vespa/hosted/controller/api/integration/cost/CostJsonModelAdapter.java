// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.cost;

import com.yahoo.slime.Cursor;
import com.yahoo.vespa.hosted.controller.api.cost.CostJsonModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Converting from cost data model to the JSON data model used in the cost REST API.
 *
 * @author smorgrav
 */
public class CostJsonModelAdapter {

    public static CostJsonModel.Application toJsonModel(ApplicationCost appCost) {
        CostJsonModel.Application app = new CostJsonModel.Application();
        app.zone = appCost.getZone();
        app.tenant = appCost.getTenant();
        app.app = appCost.getApp();
        app.tco = appCost.getTco();
        app.utilization = appCost.getUtilization();
        app.waste = appCost.getWaste();
        app.cluster = new HashMap<>();
        Map<String, ClusterCost> clusterMap = appCost.getCluster();
        for (String key : clusterMap.keySet()) {
            app.cluster.put(key, toJsonModel(clusterMap.get(key)));
        }

        return app;
    }
    
    public static void toSlime(ApplicationCost appCost, Cursor object) {
        object.setString("zone", appCost.getZone());
        object.setString("tenant", appCost.getTenant());
        object.setString("app", appCost.getApp());
        object.setLong("tco", appCost.getTco());
        object.setDouble("utilization", appCost.getUtilization());
        object.setDouble("waste", appCost.getWaste());
        Cursor clustersObject = object.setObject("cluster");
        for (Map.Entry<String, ClusterCost> clusterEntry : appCost.getCluster().entrySet())
            toSlime(clusterEntry.getValue(), clustersObject.setObject(clusterEntry.getKey()));
    }

    public static CostJsonModel.Cluster toJsonModel(ClusterCost clusterCost) {
        CostJsonModel.Cluster cluster = new CostJsonModel.Cluster();
        cluster.count = clusterCost.getCount();
        cluster.resource = clusterCost.getResource();
        cluster.utilization = clusterCost.getUtilization();
        cluster.tco = clusterCost.getTco();
        cluster.flavor = clusterCost.getFlavor();
        cluster.waste = clusterCost.getWaste();
        cluster.type = clusterCost.getType();
        cluster.util = new CostJsonModel.HardwareResources();
        cluster.util.cpu = clusterCost.getUtilCpu();
        cluster.util.mem = clusterCost.getUtilMem();
        cluster.util.disk = clusterCost.getUtilDisk();
        cluster.usage = new CostJsonModel.HardwareResources();
        cluster.usage.cpu = clusterCost.getUsageCpu();
        cluster.usage.mem = clusterCost.getUsageMem();
        cluster.usage.disk = clusterCost.getUsageDisk();
        cluster.hostnames = new ArrayList<>(clusterCost.getHostnames());
        cluster.usage.diskBusy = clusterCost.getUsageDiskBusy();
        cluster.util.diskBusy = clusterCost.getUtilDiskBusy();
        return cluster;
    }

    private static void toSlime(ClusterCost clusterCost, Cursor object) {
        object.setLong("count", clusterCost.getCount());
        object.setString("resource", clusterCost.getResource());
        object.setDouble("utilization", clusterCost.getUtilization());
        object.setLong("tco", clusterCost.getTco());
        object.setString("flavor", clusterCost.getFlavor());
        object.setLong("waste", clusterCost.getWaste());
        object.setString("type", clusterCost.getType());
        Cursor utilObject = object.setObject("util");
        utilObject.setDouble("cpu", clusterCost.getUtilCpu());
        utilObject.setDouble("mem", clusterCost.getUtilMem());
        utilObject.setDouble("disk", clusterCost.getUtilDisk());
        utilObject.setDouble("diskBusy", clusterCost.getUtilDiskBusy());
        Cursor usageObject = object.setObject("usage");
        usageObject.setDouble("cpu", clusterCost.getUsageCpu());
        usageObject.setDouble("mem", clusterCost.getUsageMem());
        usageObject.setDouble("disk", clusterCost.getUsageDisk());
        usageObject.setDouble("diskBusy", clusterCost.getUsageDiskBusy());
        Cursor hostnamesArray = object.setArray("hostnames");
        for (String hostname : clusterCost.getHostnames())
            hostnamesArray.addString(hostname);
    }

}
