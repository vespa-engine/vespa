// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.google.common.collect.ImmutableMap;
import com.yahoo.component.Version;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.vespa.hosted.controller.versions.NodeVersion;
import com.yahoo.vespa.hosted.controller.versions.NodeVersions;

import java.time.Instant;

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
    private static final String changedAtField = "changedAt";

    // Legacy fields
    private static final String environmentField = "environment";
    private static final String regionField = "region";

    public void nodeVersionsToSlime(NodeVersions nodeVersions, Cursor array) {
        for (var nodeVersion : nodeVersions.asMap().values()) {
            var nodeVersionObject = array.addObject();
            nodeVersionObject.setString(hostnameField, nodeVersion.hostname().value());
            nodeVersionObject.setString(zoneField, nodeVersion.zone().value());
            nodeVersionObject.setString(wantedVersionField, nodeVersion.wantedVersion().toFullString());
            nodeVersionObject.setLong(changedAtField, nodeVersion.changedAt().toEpochMilli());
        }
    }

    public NodeVersions nodeVersionsFromSlime(Inspector array, Version version) {
        var nodeVersions = ImmutableMap.<HostName, NodeVersion>builder();
        array.traverse((ArrayTraverser) (i, entry) -> {
            var hostname = HostName.from(entry.field(hostnameField).asString());
            var zone = zoneFromSlime(entry);
            // TODO(mpolden): Make the following fields non-optional after September 2019
            var wantedVersion = Serializers.optionalString(entry.field(wantedVersionField))
                                           .map(Version::fromString)
                                           .orElse(Version.emptyVersion);
            var changedAt = Serializers.optionalInstant(entry.field(changedAtField)).orElse(Instant.EPOCH);
            nodeVersions.put(hostname, new NodeVersion(hostname, zone, version, wantedVersion, changedAt));
        });
        return new NodeVersions(nodeVersions.build());
    }

    // TODO(mpolden): Simplify and in-line after September 2019
    private ZoneId zoneFromSlime(Inspector object) {
        var zoneInspector = object.field(zoneField);
        if (zoneInspector.valid()) {
            return ZoneId.from(zoneInspector.asString());
        }
        var regionInspector = object.field(regionField);
        var environmentInspector = object.field(environmentField);
        if (regionInspector.valid() && environmentInspector.valid()) {
            return ZoneId.from(environmentInspector.asString(), regionInspector.asString());
        }
        return ZoneId.defaultId();
    }

}
