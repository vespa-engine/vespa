// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.component.Version;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.controller.versions.NodeVersion;

import java.util.ArrayList;
import java.util.List;

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
    private static final String zoneField = "zone";
    private static final String wantedVersionField = "wantedVersion";
    private static final String suspendedAtField = "suspendedAt";

    public void nodeVersionsToSlime(List<NodeVersion> nodeVersions, Cursor array) {
        for (var nodeVersion : nodeVersions) {
            var nodeVersionObject = array.addObject();
            nodeVersionObject.setString(hostnameField, nodeVersion.hostname().value());
            nodeVersionObject.setString(zoneField, nodeVersion.zone().value());
            nodeVersionObject.setString(wantedVersionField, nodeVersion.wantedVersion().toFullString());
            nodeVersion.suspendedAt().ifPresent(suspendedAt -> nodeVersionObject.setLong(suspendedAtField,
                                                                                         suspendedAt.toEpochMilli()));
        }
    }

    public List<NodeVersion> nodeVersionsFromSlime(Inspector array, Version version) {
        List<NodeVersion> nodeVersions = new ArrayList<>();
        array.traverse((ArrayTraverser) (i, entry) -> {
            var hostname = HostName.of(entry.field(hostnameField).asString());
            var zone = ZoneId.from(entry.field(zoneField).asString());
            var wantedVersion = Version.fromString(entry.field(wantedVersionField).asString());
            var suspendedAt = SlimeUtils.optionalInstant(entry.field(suspendedAtField));
            nodeVersions.add(new NodeVersion(hostname, zone, version, wantedVersion, suspendedAt));
        });
        return nodeVersions;
    }

}
