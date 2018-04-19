// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.versions.DeploymentStatistics;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Serializes VersionStatus to and from slime
 *
 * @author mpolden
 */
public class VersionStatusSerializer {

    // VersionStatus fields
    private static final String versionsField = "versions";

    // VespaVersion fields
    private static final String releaseCommitField = "releaseCommit";
    private static final String committedAtField = "releasedAt";
    private static final String isControllerVersionField = "isCurrentControllerVersion";
    private static final String isSystemVersionField = "isCurrentSystemVersion";
    private static final String deploymentStatisticsField = "deploymentStatistics";
    private static final String confidenceField = "confidence";
    private static final String configServersField = "configServerHostnames";

    // DeploymentStatistics fields
    private static final String versionField = "version";
    private static final String failingField = "failing";
    private static final String productionField = "production";
    private static final String deployingField = "deploying";

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
        deploymentStatisticsToSlime(version.statistics(), object.setObject(deploymentStatisticsField));
        object.setString(confidenceField, version.confidence().name());
        configServersToSlime(version.configServerHostnames(), object.setArray(configServersField));
    }

    private void configServersToSlime(Set<HostName> configServerHostnames, Cursor array) {
        configServerHostnames.stream().map(HostName::value).forEach(array::addString);
    }

    private void deploymentStatisticsToSlime(DeploymentStatistics statistics, Cursor object) {
        object.setString(versionField, statistics.version().toString());
        applicationsToSlime(statistics.failing(), object.setArray(failingField));
        applicationsToSlime(statistics.production(), object.setArray(productionField));
        applicationsToSlime(statistics.deploying(), object.setArray(deployingField));
    }

    private void applicationsToSlime(Collection<ApplicationId> applications, Cursor array) {
        applications.forEach(application -> array.addString(application.serializedForm()));
    }

    private List<VespaVersion> vespaVersionsFromSlime(Inspector array) {
        List<VespaVersion> versions = new ArrayList<>();
        array.traverse((ArrayTraverser) (i, object) -> versions.add(vespaVersionFromSlime(object)));
        return Collections.unmodifiableList(versions);
    }

    private VespaVersion vespaVersionFromSlime(Inspector object) {
        return new VespaVersion(deploymentStatisticsFromSlime(object.field(deploymentStatisticsField)),
                                object.field(releaseCommitField).asString(),
                                Instant.ofEpochMilli(object.field(committedAtField).asLong()),
                                object.field(isControllerVersionField).asBool(),
                                object.field(isSystemVersionField).asBool(),
                                configServersFromSlime(object.field(configServersField)),
                                VespaVersion.Confidence.valueOf(object.field(confidenceField).asString())
        );
    }

    private Set<HostName> configServersFromSlime(Inspector array) {
        Set<HostName> configServerHostnames = new LinkedHashSet<>();
        array.traverse((ArrayTraverser) (i, entry) -> configServerHostnames.add(HostName.from(entry.asString())));
        return Collections.unmodifiableSet(configServerHostnames);
    }

    private DeploymentStatistics deploymentStatisticsFromSlime(Inspector object) {
        return new DeploymentStatistics(Version.fromString(object.field(versionField).asString()),
                                        applicationsFromSlime(object.field(failingField)),
                                        applicationsFromSlime(object.field(productionField)),
                                        applicationsFromSlime(object.field(deployingField)));
    }

    private List<ApplicationId> applicationsFromSlime(Inspector array) {
        List<ApplicationId> applications = new ArrayList<>();
        array.traverse((ArrayTraverser) (i, entry) -> applications.add(
                ApplicationId.fromSerializedForm(entry.asString()))
        );
        return Collections.unmodifiableList(applications);
    }

}
