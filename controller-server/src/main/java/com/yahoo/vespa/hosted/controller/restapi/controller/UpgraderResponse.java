// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.controller;

import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.maintenance.Upgrader;
import com.yahoo.vespa.hosted.controller.restapi.SlimeJsonResponse;

/**
 * @author mpolden
 */
public class UpgraderResponse extends SlimeJsonResponse {

    public UpgraderResponse(Upgrader upgrader) {
        super(toSlime(upgrader));
    }

    private static Slime toSlime(Upgrader upgrader) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setDouble("upgradesPerMinute", upgrader.upgradesPerMinute());
        upgrader.targetMajorVersion().ifPresent(v -> root.setLong("targetMajorVersion", v));

        Cursor array = root.setArray("confidenceOverrides");
        upgrader.confidenceOverrides().forEach((version, confidence) -> {
            Cursor object = array.addObject();
            object.setString(version.toString(), confidence.name());
        });

        return slime;
    }

}
