// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    private final Optional<Version> hostedVersion;
    private final Optional<String> stateVersion;
    private final Optional<String> dockerImage;
    private final int failCount;
    private final Optional<HardwareFailureType> hardwareFailure;
    private final boolean wantToRetire;
    
    public enum HardwareFailureType {
        
        /** There are mce log error messages */
        memory_mcelog,
        /** There are smart log error messages */
        disk_smart,
        /** There are kernel log error messages */
        disk_kernel,
        /** There is an error but its type is unknown */ 
        // TODO: Remove this when all hosts in the node repo has a failure type
        unknown
        
    }

    public Status(Generation generation,
                  Optional<Version> vespaVersion,
                  Optional<Version> hostedVersion,
                  Optional<String> stateVersion,
                  Optional<String> dockerImage,
                  int failCount,
                  Optional<HardwareFailureType> hardwareFailure,
                  boolean wantToRetire) {
        this.reboot = generation;
        this.vespaVersion = vespaVersion;
        this.hostedVersion = hostedVersion;
        this.stateVersion = stateVersion;
        this.dockerImage = dockerImage;
        this.failCount = failCount;
        this.hardwareFailure = hardwareFailure;
        this.wantToRetire = wantToRetire;
    }

    /** Returns a copy of this with the reboot generation changed */
    public Status withReboot(Generation reboot) { return new Status(reboot, vespaVersion, hostedVersion, stateVersion, dockerImage, failCount, hardwareFailure, wantToRetire); }

    /** Returns the reboot generation of this node */
    public Generation reboot() { return reboot; }

    /** Returns a copy of this with the vespa version changed */
    public Status withVespaVersion(Version version) { return new Status(reboot, Optional.of(version), hostedVersion, stateVersion, dockerImage, failCount, hardwareFailure, wantToRetire); }

    /** Returns the Vespa version installed on the node, if known */
    public Optional<Version> vespaVersion() { return vespaVersion; }

    /** Returns a copy of this with the hosted version changed */
    public Status withHostedVersion(Version version) { return new Status(reboot, vespaVersion, Optional.of(version), stateVersion, dockerImage, failCount, hardwareFailure, wantToRetire); }

    /** Returns the hosted version installed on the node, if known */
    public Optional<Version> hostedVersion() { return hostedVersion; }

    /** Returns a copy of this with the state version changed */
    public Status withStateVersion(String version) { return new Status(reboot, vespaVersion, hostedVersion, Optional.of(version), dockerImage, failCount, hardwareFailure, wantToRetire); }

    /**
     * Returns the state version the node last successfully converged with.
     * The state version contains the version-specific parts in identifying state
     * files on dist, and is of the form HOSTEDVERSION.
     * It's also used to uniquely identify a hosted Vespa release.
     */
    public Optional<String> stateVersion() { return stateVersion; }

    /** Returns a copy of this with the docker image changed */
    public Status withDockerImage(String dockerImage) { return new Status(reboot, vespaVersion, hostedVersion, stateVersion, Optional.of(dockerImage), failCount, hardwareFailure, wantToRetire); }

    /** Returns the current docker image the node is running, if known. */
    public Optional<String> dockerImage() { return dockerImage; }

    public Status withIncreasedFailCount() { return new Status(reboot, vespaVersion, hostedVersion, stateVersion, dockerImage, failCount + 1, hardwareFailure, wantToRetire); }

    public Status withDecreasedFailCount() { return new Status(reboot, vespaVersion, hostedVersion, stateVersion, dockerImage, failCount - 1, hardwareFailure, wantToRetire); }

    public Status setFailCount(Integer value) { return new Status(reboot, vespaVersion, hostedVersion, stateVersion, dockerImage, value, hardwareFailure, wantToRetire); }

    /** Returns how many times this node has been moved to the failed state. */
    public int failCount() { return failCount; }

    public Status withHardwareFailure(Optional<HardwareFailureType> hardwareFailure) { return new Status(reboot, vespaVersion, hostedVersion, stateVersion, dockerImage, failCount, hardwareFailure, wantToRetire); }

    /** Returns the type of the last hardware failure detected on this node, or empty if none */
    public Optional<HardwareFailureType> hardwareFailure() { return hardwareFailure; }

    /** Returns a copy of this with the want to retire flag changed */
    public Status withWantToRetire(boolean wantToRetire) {
        return new Status(reboot, vespaVersion, hostedVersion, stateVersion, dockerImage, failCount, hardwareFailure, wantToRetire);
    }

    /**
     * Returns whether this node should be retired at some point in the future. It does NOT indicate whether the node
     * is actually retired.
     */
    public boolean wantToRetire() {
        return wantToRetire;
    }

    /** Returns the initial status of a newly provisioned node */
    public static Status initial() { return new Status(Generation.inital(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 0, Optional.empty(), false); }

}
