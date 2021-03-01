// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi;

import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.vespa.hosted.provision.NodeRepository;

/**
 * Returns tenant archive URIs.
 *
 * @author freva
 */
public class ArchiveResponse extends SlimeJsonResponse {

    public ArchiveResponse(NodeRepository nodeRepository) {
        Cursor archivesArray = slime.setObject().setArray("archives");
        nodeRepository.archiveUris().getArchiveUris().forEach((tenant, uri) -> {
            Cursor archiveObject = archivesArray.addObject();
            archiveObject.setString("tenant", tenant.value());
            archiveObject.setString("uri", uri);
        });
    }
}
