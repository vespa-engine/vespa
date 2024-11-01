// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.google.common.collect.ImmutableMap;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.provision.backup.Snapshot;
import com.yahoo.vespa.hosted.provision.backup.SnapshotId;
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
    private static final String INSTANCE_FIELD = "instance";
    private static final String CLUSTER_FIELD = "cluster";
    private static final String CLUSTER_INDEX_FIELD = "clusterIndex";
    private static final String HISTORY_FIELD = "history";
    private static final String EVENT_FIELD = "event";
    private static final String AT_FIELD = "at";
    private static final String CLOUD_ACCOUNT_FIELD = "cloudAccount";

    private SnapshotSerializer() {}

    public static Snapshot fromInspector(Inspector object, CloudAccount systemAccount) {
        ImmutableMap.Builder<Snapshot.State, Snapshot.History.Event> history = ImmutableMap.builder();
        object.field(HISTORY_FIELD).traverse((ArrayTraverser) (idx, inspector) -> {
            Snapshot.State type = stateFromSlime(inspector.field(EVENT_FIELD).asString());
            Instant at = Instant.ofEpochMilli(inspector.field(AT_FIELD).asLong());
            history.put(type, new Snapshot.History.Event(type, at));
        });
        // TODO(mpolden): Require field after 2024-12-01
        CloudAccount cloudAccount = SlimeUtils.optionalString(object.field(CLOUD_ACCOUNT_FIELD))
                                              .map(CloudAccount::from)
                                              .orElse(systemAccount);
        return new Snapshot(SnapshotId.of(object.field(ID_FIELD).asString()),
                            HostName.of(object.field(HOSTNAME_FIELD).asString()),
                            stateFromSlime(object.field(STATE_FIELD).asString()),
                            new Snapshot.History(history.build()),
                            new ClusterId(ApplicationId.fromSerializedForm(object.field(INSTANCE_FIELD).asString()),
                                          ClusterSpec.Id.from(object.field(CLUSTER_FIELD).asString())),
                            (int) object.field(CLUSTER_INDEX_FIELD).asLong(),
                            cloudAccount
        );
    }

    public static Snapshot fromSlime(Slime slime, CloudAccount cloudAccount) {
        return fromInspector(slime.get(), cloudAccount);
    }

    public static Slime toSlime(Snapshot snapshot) {
        Slime slime = new Slime();
        toSlime(snapshot, slime.setObject());
        return slime;
    }

    public static List<Snapshot> listFromSlime(Slime slime, CloudAccount systemAccount) {
        Cursor root = slime.get();
        return SlimeUtils.entriesStream(root.field(SNAPSHOTS_FIELD))
                         .map(i -> SnapshotSerializer.fromInspector(i, systemAccount))
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
        object.setString(ID_FIELD, snapshot.id().toString());
        object.setString(HOSTNAME_FIELD, snapshot.hostname().value());
        object.setString(STATE_FIELD, asString(snapshot.state()));
        object.setString(INSTANCE_FIELD, snapshot.cluster().application().serializedForm());
        object.setString(CLUSTER_FIELD, snapshot.cluster().cluster().value());
        object.setLong(CLUSTER_INDEX_FIELD, snapshot.clusterIndex());
        Cursor historyArray = object.setArray(HISTORY_FIELD);
        snapshot.history().events().values().forEach(event -> {
            Cursor eventObject = historyArray.addObject();
            eventObject.setString(EVENT_FIELD, asString(event.type()));
            eventObject.setLong(AT_FIELD, event.at().toEpochMilli());
        });
        object.setString(CLOUD_ACCOUNT_FIELD, snapshot.cloudAccount().value());
    }

    public static String asString(Snapshot.State state) {
        return switch (state) {
            case creating -> "creating";
            case created -> "created";
            case restoring -> "restoring";
            case restored -> "restored";
        };
    }

    private static Snapshot.State stateFromSlime(String value) {
        return switch (value) {
            case "creating" -> Snapshot.State.creating;
            case "created" -> Snapshot.State.created;
            case "restoring" -> Snapshot.State.restoring;
            case "restored" -> Snapshot.State.restored;
            default -> throw new IllegalArgumentException("Unknown snapshot state '" + value + "'");
        };
    }

}
