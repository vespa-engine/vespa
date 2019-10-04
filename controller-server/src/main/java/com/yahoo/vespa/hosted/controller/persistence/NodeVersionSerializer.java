// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.component.Version;
import com.yahoo.config.provision.HostName;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.vespa.hosted.controller.versions.NodeVersion;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Serializer for {@link com.yahoo.vespa.hosted.controller.versions.NodeVersion}.
 *
 * @author mpolden
 */
public class NodeVersionSerializer {

    // WARNING: Since there are multiple servers in a ZooKeeper cluster and they upgrade one by one
    //          (and rewrite all nodes on startup), changes to the serialized format must be made
    //          such that what is serialized on version N+1 can be read by version N:
    //          - ADDING FIELDS: Always ok
    //          - REMOVING FIELDS: Stop reading the field first. Stop writing it on a later version.
    //          - CHANGING THE FORMAT OF A FIELD: Don't do it bro.

    private static final String hostnameField = "hostname";
    private static final String currentVersionField = "currentVersion";
    private static final String wantedVersionField = "wantedVersion";
    private static final String changedAtField = "changedAt";

    public void nodeVersionsToSlime(Collection<NodeVersion> nodeVersions, Cursor array, boolean writeCurrentVersion) {
        for (var nodeVersion : nodeVersions) {
            var nodeVersionObject = array.addObject();
            nodeVersionObject.setString(hostnameField, nodeVersion.hostname().value());
            if (writeCurrentVersion) {
                nodeVersionObject.setString(currentVersionField, nodeVersion.currentVersion().toFullString());
            }
            nodeVersionObject.setString(wantedVersionField, nodeVersion.wantedVersion().toFullString());
            nodeVersionObject.setLong(changedAtField, nodeVersion.changedAt().toEpochMilli());
        }
    }

    public List<NodeVersion> nodeVersionsFromSlime(Inspector object, Optional<Version> version) {
        var nodeVersions = new ArrayList<NodeVersion>();
        object.traverse((ArrayTraverser) (i, entry) -> {
            var hostname = HostName.from(entry.field(hostnameField).asString());
            var currentVersion = version.orElseGet(() -> Version.fromString(entry.field(currentVersionField).asString()));
            var wantedVersion = Version.fromString(entry.field(wantedVersionField).asString());
            var changedAt = Instant.ofEpochMilli(entry.field(changedAtField).asLong());
            nodeVersions.add(new NodeVersion(hostname, currentVersion, wantedVersion, changedAt));
        });
        return Collections.unmodifiableList(nodeVersions);
    }

}
