// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.v2;

import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.provision.maintenance.InfrastructureVersions;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Comparator;
import java.util.Map;

/**
 * A response containing infrastructure versions
 *
 * @author freva
 */
public class UpgradeResponse extends HttpResponse {

    private final InfrastructureVersions infrastructureVersions;

    public UpgradeResponse(InfrastructureVersions infrastructureVersions) {
        super(200);
        this.infrastructureVersions = infrastructureVersions;
    }

    @Override
    public void render(OutputStream stream) throws IOException {
        Slime slime = new Slime();
        Cursor root = slime.setObject();

        Cursor versionsObject = root.setObject("versions");
        infrastructureVersions.getTargetVersions().forEach((nodeType, version) -> versionsObject.setString(nodeType.name(), version.toFullString()));

        new JsonFormat(true).encode(stream, slime);
    }

    @Override
    public String getContentType() { return "application/json"; }

}
