// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

/**
 * Implementations of this interface add constraints to when a node with
 * pending global merges may be implicitly transitioned to Maintenance
 * state in the default bucket space. This is to avoid flip-flopping nodes
 * between being available and in maintenance when merge statistics
 * change in a running system.
 */
public interface MaintenanceTransitionConstraint {

    boolean maintenanceTransitionAllowed(int contentNodeIndex);

}
