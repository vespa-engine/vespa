// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node;

import com.yahoo.component.Version;
import com.yahoo.config.provision.DockerImage;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Information about current status of a node. This is immutable.
 *
 * @author bratseth
 */
public class Status {

    private final Generation reboot;
    private final Optional<Version> vespaVersion;
    private final Optional<DockerImage> dockerImage;
    private final int failCount;
    private final boolean wantToRetire;
    private final boolean wantToDeprovision;
    private final OsVersion osVersion;
    private final Optional<Instant> firmwareVerifiedAt;

    public Status(Generation generation,
                  Optional<Version> vespaVersion,
                  Optional<DockerImage> dockerImage,
                  int failCount,
                  boolean wantToRetire,
                  boolean wantToDeprovision,
                  OsVersion osVersion,
                  Optional<Instant> firmwareVerifiedAt) {
        this.reboot = Objects.requireNonNull(generation, "Generation must be non-null");
        this.vespaVersion = Objects.requireNonNull(vespaVersion, "Vespa version must be non-null").filter(v -> !Version.emptyVersion.equals(v));
        this.dockerImage = Objects.requireNonNull(dockerImage, "Docker image must be non-null").filter(d -> !DockerImage.EMPTY.equals(d));
        this.failCount = failCount;
        this.wantToRetire = wantToRetire;
        this.wantToDeprovision = wantToDeprovision;
        this.osVersion = Objects.requireNonNull(osVersion, "OS version must be non-null");
        this.firmwareVerifiedAt = Objects.requireNonNull(firmwareVerifiedAt, "Firmware check instant must be non-null");
    }

    /** Returns a copy of this with the reboot generation changed */
    public Status withReboot(Generation reboot) { return new Status(reboot, vespaVersion, dockerImage, failCount, wantToRetire, wantToDeprovision, osVersion, firmwareVerifiedAt); }

    /** Returns the reboot generation of this node */
    public Generation reboot() { return reboot; }

    /** Returns a copy of this with the vespa version changed */
    public Status withVespaVersion(Version version) { return new Status(reboot, Optional.of(version), dockerImage, failCount, wantToRetire, wantToDeprovision, osVersion, firmwareVerifiedAt); }

    /** Returns the Vespa version installed on the node, if known */
    public Optional<Version> vespaVersion() { return vespaVersion; }

    /** Returns a copy of this with the docker image changed */
    public Status withDockerImage(DockerImage dockerImage) { return new Status(reboot, vespaVersion, Optional.of(dockerImage), failCount, wantToRetire, wantToDeprovision, osVersion, firmwareVerifiedAt); }

    /** Returns the docker image the node is running, if known */
    public Optional<DockerImage> dockerImage() { return dockerImage; }

    public Status withIncreasedFailCount() { return new Status(reboot, vespaVersion, dockerImage, failCount + 1, wantToRetire, wantToDeprovision, osVersion, firmwareVerifiedAt); }

    public Status withDecreasedFailCount() { return new Status(reboot, vespaVersion, dockerImage, failCount - 1, wantToRetire, wantToDeprovision, osVersion, firmwareVerifiedAt); }

    public Status setFailCount(Integer value) { return new Status(reboot, vespaVersion, dockerImage, value, wantToRetire, wantToDeprovision, osVersion, firmwareVerifiedAt); }

    /** Returns how many times this node has been moved to the failed state. */
    public int failCount() { return failCount; }

    /** Returns a copy of this with the want to retire flag changed */
    public Status withWantToRetire(boolean wantToRetire) {
        return new Status(reboot, vespaVersion, dockerImage, failCount, wantToRetire, wantToDeprovision, osVersion, firmwareVerifiedAt);
    }

    /**
     * Returns whether this node should be retired at some point in the future. It does NOT indicate whether the node
     * is actually retired.
     */
    public boolean wantToRetire() {
        return wantToRetire;
    }

    /** Returns a copy of this with the want to de-provision flag changed */
    public Status withWantToDeprovision(boolean wantToDeprovision) {
        return new Status(reboot, vespaVersion, dockerImage, failCount, wantToRetire, wantToDeprovision, osVersion, firmwareVerifiedAt);
    }

    /**
     * Returns whether this node should be de-provisioned when possible.
     */
    public boolean wantToDeprovision() {
        return wantToDeprovision;
    }

    /** Returns a copy of this with the OS version set to given version */
    public Status withOsVersion(OsVersion version) {
        return new Status(reboot, vespaVersion, dockerImage, failCount, wantToRetire, wantToDeprovision, version, firmwareVerifiedAt);
    }

    /** Returns the OS version of this node */
    public OsVersion osVersion() {
        return osVersion;
    }

    /** Returns a copy of this with the firmwareVerifiedAt set to the given instant. */
    public Status withFirmwareVerifiedAt(Instant instant) {
        return new Status(reboot, vespaVersion, dockerImage, failCount, wantToRetire, wantToDeprovision, osVersion, Optional.of(instant));
    }

    /** Returns the last time this node had firmware that was verified to be up to date. */
    public Optional<Instant> firmwareVerifiedAt() {
        return firmwareVerifiedAt;
    }

    /** Returns the initial status of a newly provisioned node */
    public static Status initial() {
        return new Status(Generation.initial(), Optional.empty(), Optional.empty(), 0, false,
                          false, OsVersion.EMPTY, Optional.empty());
    }

}
