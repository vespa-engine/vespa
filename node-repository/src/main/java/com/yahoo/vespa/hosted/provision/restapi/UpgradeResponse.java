// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi;

import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.provision.maintenance.InfrastructureVersions;
import com.yahoo.vespa.hosted.provision.provisioning.ContainerImages;
import com.yahoo.vespa.hosted.provision.os.OsVersions;

import java.io.IOException;
import java.io.OutputStream;

/**
 * A response containing targets for infrastructure Vespa version and OS version.
 *
 * @author freva
 */
public class UpgradeResponse extends HttpResponse {

    private final InfrastructureVersions infrastructureVersions;
    private final OsVersions osVersions;

    public UpgradeResponse(InfrastructureVersions infrastructureVersions, OsVersions osVersions, ContainerImages containerImages) {
        super(200);
        this.infrastructureVersions = infrastructureVersions;
        this.osVersions = osVersions;
    }

    @Override
    public void render(OutputStream stream) throws IOException {
        Slime slime = new Slime();
        Cursor root = slime.setObject();

        Cursor versionsObject = root.setObject("versions");
        infrastructureVersions.getTargetVersions().forEach((nodeType, version) -> versionsObject.setString(nodeType.name(), version.toFullString()));

        Cursor osVersionsObject = root.setObject("osVersions");
        osVersions.readChange().targets().forEach((nodeType, target) -> osVersionsObject.setString(nodeType.name(), target.version().toFullString()));

        root.setObject("dockerImages"); // Unused, but present to avoid breaking API

        new JsonFormat(true).encode(stream, slime);
    }

    @Override
    public String getContentType() { return "application/json"; }

}
