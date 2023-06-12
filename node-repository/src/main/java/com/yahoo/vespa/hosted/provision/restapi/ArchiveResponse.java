// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi;

import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.archive.ArchiveUris;

import java.util.Map;

/**
 * Returns tenant archive URIs.
 *
 * @author freva
 */
public class ArchiveResponse extends SlimeJsonResponse {

    public ArchiveResponse(NodeRepository nodeRepository) {
        ArchiveUris archiveUris = nodeRepository.archiveUriManager().archiveUris();
        Cursor archivesArray = slime.setObject().setArray("archives");

        archiveUris.tenantArchiveUris().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
            Cursor archiveObject = archivesArray.addObject();
            archiveObject.setString("tenant", entry.getKey().value());
            archiveObject.setString("uri", entry.getValue());
        });
        archiveUris.accountArchiveUris().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
            Cursor archiveObject = archivesArray.addObject();
            archiveObject.setString("account", entry.getKey().value());
            archiveObject.setString("uri", entry.getValue());
        });
    }
}
