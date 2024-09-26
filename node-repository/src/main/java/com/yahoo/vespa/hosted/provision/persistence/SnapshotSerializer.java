// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.provision.backup.Snapshot;
import com.yahoo.vespa.hosted.provision.node.ClusterId;

import java.time.Instant;
import java.util.List;

/**
 * @author mpolden
 */
public class SnapshotSerializer {

    private static final String SNAPSHOTS_FIELD = "snapshots";
    private static final String ID_FIELD = "id";
    private static final String HOSTNAME_FIELD = "hostname";
    private static final String STATE_FIELD = "state";
    private static final String CREATED_AT_FIELD = "createdAt";
    private static final String INSTANCE_FIELD = "instance";
    private static final String CLUSTER_FIELD = "cluster";
    private static final String CLUSTER_INDEX_FIELD = "clusterIndex";

    private SnapshotSerializer() {}

    public static Snapshot fromInspector(Inspector object) {
        return new Snapshot(object.field(ID_FIELD).asString(),
                            HostName.of(object.field(HOSTNAME_FIELD).asString()),
                            stateFromSlime(object.field(STATE_FIELD).asString()),
                            Instant.ofEpochMilli(object.field(CREATED_AT_FIELD).asLong()),
                            new ClusterId(ApplicationId.fromSerializedForm(object.field(INSTANCE_FIELD).asString()),
                                          ClusterSpec.Id.from(object.field(CLUSTER_FIELD).asString())),
                            (int) object.field(CLUSTER_INDEX_FIELD).asLong());
    }

    public static Snapshot fromSlime(Slime slime) {
        return fromInspector(slime.get());
    }

    public static Slime toSlime(Snapshot snapshot) {
        Slime slime = new Slime();
        toSlime(snapshot, slime.setObject());
        return slime;
    }

    public static List<Snapshot> listFromSlime(Slime slime) {
        Cursor root = slime.get();
        return SlimeUtils.entriesStream(root.field(SNAPSHOTS_FIELD))
                         .map(SnapshotSerializer::fromInspector)
                         .toList();
    }

    public static Slime toSlime(List<Snapshot> snapshots) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        Cursor array = root.setArray(SNAPSHOTS_FIELD);
        for (var snapshot : snapshots) {
            toSlime(snapshot, array.addObject());
        }
        return slime;
    }

    public static void toSlime(Snapshot snapshot, Cursor object) {
        object.setString(ID_FIELD, snapshot.id());
        object.setString(HOSTNAME_FIELD, snapshot.hostname().value());
        object.setString(STATE_FIELD, asString(snapshot.state()));
        object.setLong(CREATED_AT_FIELD, snapshot.createdAt().toEpochMilli());
        object.setString(INSTANCE_FIELD, snapshot.cluster().application().serializedForm());
        object.setString(CLUSTER_FIELD, snapshot.cluster().cluster().value());
        object.setLong(CLUSTER_INDEX_FIELD, snapshot.clusterIndex());
    }

    public static String asString(Snapshot.State state) {
        return switch (state) {
            case creating -> "creating";
            case failed -> "failed";
            case created -> "created";
            case restoring -> "restoring";
            case restoreFailed -> "restoreFailed";
            case restored -> "restored";
        };
    }

    private static Snapshot.State stateFromSlime(String value) {
        return switch (value) {
            case "creating" -> Snapshot.State.creating;
            case "failed" -> Snapshot.State.failed;
            case "created" -> Snapshot.State.created;
            case "restoring" -> Snapshot.State.restoring;
            case "restoreFailed" -> Snapshot.State.restoreFailed;
            case "restored" -> Snapshot.State.restored;
            default -> throw new IllegalArgumentException("Unknown snapshot state '" + value + "'");
        };
    }

}
