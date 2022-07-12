// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.controller;

import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ApplicationStats;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Load;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeRepoStats;

/**
 * A response containing statistics about this controller and its zones.
 *
 * @author bratseth
 */
public class StatsResponse extends SlimeJsonResponse {

    public StatsResponse(Controller controller) {
        super(toSlime(controller));
    }

    private static Slime toSlime(Controller controller) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        Cursor zonesArray = root.setArray("zones");
        for (ZoneId zone : controller.zoneRegistry().zones().reachable().ids()) {
            NodeRepoStats stats = controller.serviceRegistry().configServer().nodeRepository().getStats(zone);
            if (stats.applicationStats().isEmpty()) continue; // skip empty zones
            Cursor zoneObject = zonesArray.addObject();
            zoneObject.setString("id", zone.toString());
            zoneObject.setDouble("totalCost", stats.totalCost());
            zoneObject.setDouble("totalAllocatedCost", stats.totalAllocatedCost());
            toSlime(stats.load(), zoneObject.setObject("load"));
            toSlime(stats.activeLoad(), zoneObject.setObject("activeLoad"));
            Cursor applicationsArray = zoneObject.setArray("applications");
            for (var applicationStats : stats.applicationStats())
                toSlime(applicationStats, applicationsArray.addObject());
        }
        return slime;
    }

    private static void toSlime(ApplicationStats stats, Cursor applicationObject) {
        applicationObject.setString("id", stats.id().toFullString());
        toSlime(stats.load(), applicationObject.setObject("load"));
        applicationObject.setDouble("cost", stats.cost());
        applicationObject.setDouble("unutilizedCost", stats.unutilizedCost());
    }

    private static void toSlime(Load load, Cursor loadObject) {
        loadObject.setDouble("cpu", load.cpu());
        loadObject.setDouble("memory", load.memory());
        loadObject.setDouble("disk", load.disk());
    }

}
