// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node;

/**
 * The enum of agents making changes to the system.
 * 
 * @author bratseth
 */
public enum Agent {

    operator, // A hosted Vespa operator. Some logic recognizes these events.
    application, // An application package change deployment
    system, // An unspecified system agent

    // Specific system agents:
    NodeFailer,
    NodeHealthTracker,
    Rebalancer,
    DirtyExpirer,
    FailedExpirer,
    InactiveExpirer,
    ProvisionedExpirer,
    ReservationExpirer,
    ParkedExpirer,
    HostCapacityMaintainer,
    HostResumeProvisioner,
    RetiringOsUpgrader,
    RebuildingOsUpgrader,
    SpareCapacityMaintainer,
    SwitchRebalancer,
    HostEncrypter,

}
