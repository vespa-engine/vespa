// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.v2;

import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.provision.maintenance.InfrastructureVersions;
import com.yahoo.vespa.hosted.provision.provisioning.DockerImages;
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
    private final DockerImages dockerImages;

    public UpgradeResponse(InfrastructureVersions infrastructureVersions, OsVersions osVersions, DockerImages dockerImages) {
        super(200);
        this.infrastructureVersions = infrastructureVersions;
        this.osVersions = osVersions;
        this.dockerImages = dockerImages;
    }

    @Override
    public void render(OutputStream stream) throws IOException {
        Slime slime = new Slime();
        Cursor root = slime.setObject();

        Cursor versionsObject = root.setObject("versions");
        infrastructureVersions.getTargetVersions().forEach((nodeType, version) -> versionsObject.setString(nodeType.name(), version.toFullString()));

        Cursor osVersionsObject = root.setObject("osVersions");
        osVersions.targets().forEach((nodeType, osVersion) -> osVersionsObject.setString(nodeType.name(), osVersion.toFullString()));


        Cursor dockerImagesObject = root.setObject("dockerImages");
        dockerImages.getDockerImages().forEach((nodeType, image) -> dockerImagesObject.setString(nodeType.name(), image.asString()));

        new JsonFormat(true).encode(stream, slime);
    }

    @Override
    public String getContentType() { return "application/json"; }

}
