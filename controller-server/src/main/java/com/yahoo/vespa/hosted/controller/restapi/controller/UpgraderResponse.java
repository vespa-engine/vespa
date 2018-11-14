// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.controller;

import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.maintenance.Upgrader;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author mpolden
 */
public class UpgraderResponse extends HttpResponse {

    private final Upgrader upgrader;

    public UpgraderResponse(Upgrader upgrader) {
        super(200);
        this.upgrader = upgrader;
    }

    @Override
    public void render(OutputStream outputStream) throws IOException {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setDouble("upgradesPerMinute", upgrader.upgradesPerMinute());
        upgrader.targetMajorVersion().ifPresent(v -> root.setLong("targetMajorVersion", v));

        Cursor array = root.setArray("confidenceOverrides");
        upgrader.confidenceOverrides().forEach((version, confidence) -> {
            Cursor object = array.addObject();
            object.setString(version.toString(), confidence.name());
        });

        new JsonFormat(true).encode(outputStream, slime);
    }

    @Override
    public String getContentType() {
        return "application/json";
    }
}
