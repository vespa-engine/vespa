// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.component.Version;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.controller.versions.NodeVersion;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Serializer for {@link VersionStatus}.
 *
 * @author mpolden
 */
public class VersionStatusSerializer {

    // WARNING: Since there are multiple servers in a ZooKeeper cluster and they upgrade one by one
    //          (and rewrite all nodes on startup), changes to the serialized format must be made
    //          such that what is serialized on version N+1 can be read by version N:
    //          - ADDING FIELDS: Always ok
    //          - REMOVING FIELDS: Stop reading the field first. Stop writing it on a later version.
    //          - CHANGING THE FORMAT OF A FIELD: Don't do it bro.

    // VersionStatus fields
    private static final String versionsField = "versions";

    // VespaVersion fields
    private static final String releaseCommitField = "releaseCommit";
    private static final String committedAtField = "releasedAt";
    private static final String isControllerVersionField = "isCurrentControllerVersion";
    private static final String isSystemVersionField = "isCurrentSystemVersion";
    private static final String isReleasedField = "isReleased";
    private static final String deploymentStatisticsField = "deploymentStatistics";
    private static final String confidenceField = "confidence";

    // NodeVersions fields
    private static final String nodeVersionsField = "nodeVersions";

    // DeploymentStatistics fields
    private static final String versionField = "version";
    private static final String failingField = "failing";
    private static final String productionField = "production";
    private static final String deployingField = "deploying";

    private final NodeVersionSerializer nodeVersionSerializer;

    public VersionStatusSerializer(NodeVersionSerializer nodeVersionSerializer) {
        this.nodeVersionSerializer = Objects.requireNonNull(nodeVersionSerializer, "nodeVersionSerializer must be non-null");
    }

    public Slime toSlime(VersionStatus status) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        versionsToSlime(status.versions(), root.setArray(versionsField));
        return slime;
    }

    public VersionStatus fromSlime(Slime slime) {
        Inspector root = slime.get();
        return new VersionStatus(vespaVersionsFromSlime(root.field(versionsField)));
    }

    private void versionsToSlime(List<VespaVersion> versions, Cursor array) {
        versions.forEach(version -> vespaVersionToSlime(version, array.addObject()));
    }

    private void vespaVersionToSlime(VespaVersion version, Cursor object) {
        object.setString(releaseCommitField, version.releaseCommit());
        object.setLong(committedAtField, version.committedAt().toEpochMilli());
        object.setBool(isControllerVersionField, version.isControllerVersion());
        object.setBool(isSystemVersionField, version.isSystemVersion());
        object.setBool(isReleasedField, version.isReleased());
        deploymentStatisticsToSlime(version.versionNumber(), object.setObject(deploymentStatisticsField));
        object.setString(confidenceField, version.confidence().name());
        nodeVersionsToSlime(version.nodeVersions(), object.setArray(nodeVersionsField));
    }

    private void nodeVersionsToSlime(List<NodeVersion> nodeVersions, Cursor array) {
        nodeVersionSerializer.nodeVersionsToSlime(nodeVersions, array);
    }

    private void deploymentStatisticsToSlime(Version version, Cursor object) {
        object.setString(versionField, version.toString());
        // TODO jonmv: Remove the below.
        object.setArray(failingField);
        object.setArray(productionField);
        object.setArray(deployingField);
    }

    private List<VespaVersion> vespaVersionsFromSlime(Inspector array) {
        List<VespaVersion> versions = new ArrayList<>();
        array.traverse((ArrayTraverser) (i, object) -> versions.add(vespaVersionFromSlime(object)));
        return Collections.unmodifiableList(versions);
    }

    private VespaVersion vespaVersionFromSlime(Inspector object) {
        var version = Version.fromString(object.field(deploymentStatisticsField).field(versionField).asString());
        return new VespaVersion(version,
                                object.field(releaseCommitField).asString(),
                                SlimeUtils.instant(object.field(committedAtField)),
                                object.field(isControllerVersionField).asBool(),
                                object.field(isSystemVersionField).asBool(),
                                object.field(isReleasedField).asBool(),
                                nodeVersionSerializer.nodeVersionsFromSlime(object.field(nodeVersionsField), version),
                                VespaVersion.Confidence.valueOf(object.field(confidenceField).asString())
        );
    }

}
