// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import java.util.Objects;

/**
 * Wrapper for a remote (i.e. REST API) cluster controller task whose
 * completion depends on side effects by the task becoming visible in
 * the cluster before a response can be sent. Each such task is associated
 * with a particular cluster state version number representing a lower bound
 * on the published state containing the side effect. Each task is also
 * associated with a completion deadline.
 */
class VersionDependentTaskCompletion {
    private final long minimumVersion;
    private final RemoteClusterControllerTask task;
    private final long deadlineTimePointMs;

    VersionDependentTaskCompletion(long minimumVersion, RemoteClusterControllerTask task, long maxDeadlineTimePointMs) {
        this.minimumVersion = minimumVersion;
        this.task = task;
        this.deadlineTimePointMs = task.getDeadline().map(deadline ->
                Math.max(0, Math.min(deadline.toEpochMilli(), maxDeadlineTimePointMs)))
                .orElse(maxDeadlineTimePointMs);
    }

    long getMinimumVersion() {
        return minimumVersion;
    }

    RemoteClusterControllerTask getTask() {
        return task;
    }

    long getDeadlineTimePointMs() {
        return deadlineTimePointMs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VersionDependentTaskCompletion that = (VersionDependentTaskCompletion) o;
        return minimumVersion == that.minimumVersion &&
                Objects.equals(task, that.task);
    }

    @Override
    public int hashCode() {
        return Objects.hash(minimumVersion, task);
    }
}
