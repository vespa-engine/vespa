// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node;

import com.yahoo.component.Version;

import javax.annotation.concurrent.Immutable;
import java.util.Optional;

/**
 * Information about current status of a node
 *
 * @author bratseth
 */
@Immutable
public class Status {

    private final Generation reboot;
    private final Optional<Version> vespaVersion;
    private final int failCount;
    private final Optional<HardwareFailureType> hardwareFailure;
    private final boolean wantToRetire;
    private final boolean wantToDeprovision;
    
    public enum HardwareFailureType {
        
        /** There are mce log error messages */
        memory_mcelog,
        /** There are smart log error messages */
        disk_smart,
        /** There are kernel log error messages */
        disk_kernel
        
    }

    public Status(Generation generation,
                  Optional<Version> vespaVersion,
                  int failCount,
                  Optional<HardwareFailureType> hardwareFailure,
                  boolean wantToRetire,
                  boolean wantToDeprovision) {
        this.reboot = generation;
        this.vespaVersion = vespaVersion;
        this.failCount = failCount;
        this.hardwareFailure = hardwareFailure;
        this.wantToRetire = wantToRetire;
        this.wantToDeprovision = wantToDeprovision;
    }

    /** Returns a copy of this with the reboot generation changed */
    public Status withReboot(Generation reboot) { return new Status(reboot, vespaVersion, failCount, hardwareFailure, wantToRetire, wantToDeprovision); }

    /** Returns the reboot generation of this node */
    public Generation reboot() { return reboot; }

    /** Returns a copy of this with the vespa version changed */
    public Status withVespaVersion(Version version) { return new Status(reboot, Optional.of(version), failCount, hardwareFailure, wantToRetire, wantToDeprovision); }

    /** Returns the Vespa version installed on the node, if known */
    public Optional<Version> vespaVersion() { return vespaVersion; }

    public Status withIncreasedFailCount() { return new Status(reboot, vespaVersion, failCount + 1, hardwareFailure, wantToRetire, wantToDeprovision); }

    public Status withDecreasedFailCount() { return new Status(reboot, vespaVersion, failCount - 1, hardwareFailure, wantToRetire, wantToDeprovision); }

    public Status setFailCount(Integer value) { return new Status(reboot, vespaVersion, value, hardwareFailure, wantToRetire, wantToDeprovision); }

    /** Returns how many times this node has been moved to the failed state. */
    public int failCount() { return failCount; }

    public Status withHardwareFailure(Optional<HardwareFailureType> hardwareFailure) { return new Status(reboot, vespaVersion, failCount, hardwareFailure, wantToRetire, wantToDeprovision); }

    /** Returns the type of the last hardware failure detected on this node, or empty if none */
    public Optional<HardwareFailureType> hardwareFailure() { return hardwareFailure; }

    /** Returns a copy of this with the want to retire flag changed */
    public Status withWantToRetire(boolean wantToRetire) {
        return new Status(reboot, vespaVersion, failCount, hardwareFailure, wantToRetire, wantToDeprovision);
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
        return new Status(reboot, vespaVersion, failCount, hardwareFailure, wantToRetire, wantToDeprovision);
    }

    /**
     * Returns whether this node should be de-provisioned when possible.
     */
    public boolean wantToDeprovision() {
        return wantToDeprovision;
    }

    /** Returns the initial status of a newly provisioned node */
    public static Status initial() { return new Status(Generation.inital(), Optional.empty(), 0, Optional.empty(), false, false); }

}
