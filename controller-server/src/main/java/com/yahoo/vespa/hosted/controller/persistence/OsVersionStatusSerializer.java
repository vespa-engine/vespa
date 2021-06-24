// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.versions.NodeVersion;
import com.yahoo.vespa.hosted.controller.versions.OsVersion;
import com.yahoo.vespa.hosted.controller.versions.OsVersionStatus;

import java.util.List;
import java.util.Objects;

/**
 * Serializer for {@link OsVersionStatus}.
 *
 * @author mpolden
 */
public class OsVersionStatusSerializer {

    // WARNING: Since there are multiple servers in a ZooKeeper cluster and they upgrade one by one
    //          (and rewrite all nodes on startup), changes to the serialized format must be made
    //          such that what is serialized on version N+1 can be read by version N:
    //          - ADDING FIELDS: Always ok
    //          - REMOVING FIELDS: Stop reading the field first. Stop writing it on a later version.
    //          - CHANGING THE FORMAT OF A FIELD: Don't do it bro.

    private static final String versionsField = "versions";
    private static final String nodeVersionsField = "nodeVersions";

    private final OsVersionSerializer osVersionSerializer;
    private final NodeVersionSerializer nodeVersionSerializer;

    public OsVersionStatusSerializer(OsVersionSerializer osVersionSerializer, NodeVersionSerializer nodeVersionSerializer) {
        this.osVersionSerializer = Objects.requireNonNull(osVersionSerializer, "osVersionSerializer must be non-null");
        this.nodeVersionSerializer = Objects.requireNonNull(nodeVersionSerializer, "nodeVersionSerializer must be non-null");
    }

    public Slime toSlime(OsVersionStatus status) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        Cursor versions = root.setArray(versionsField);
        status.versions().forEach((version, nodes) -> {
            Cursor object = versions.addObject();
            osVersionSerializer.toSlime(version, object);
            nodeVersionSerializer.nodeVersionsToSlime(nodes, object.setArray(nodeVersionsField));
        });
        return slime;
    }

    public OsVersionStatus fromSlime(Slime slime) {
        return new OsVersionStatus(osVersionsFromSlime(slime.get().field(versionsField)));
    }

    private ImmutableMap<OsVersion, List<NodeVersion>> osVersionsFromSlime(Inspector array) {
        var versions = ImmutableSortedMap.<OsVersion, List<NodeVersion>>naturalOrder();
        array.traverse((ArrayTraverser) (i, object) -> {
            OsVersion osVersion = osVersionSerializer.fromSlime(object);
            versions.put(osVersion, nodeVersionSerializer.nodeVersionsFromSlime(object.field(nodeVersionsField), osVersion.version()));
        });
        return versions.build();
    }

}
