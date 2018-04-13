// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration;

import java.util.Objects;

/**
 * @author jvenstad
 */
public interface BuildService {

    /**
     * Enqueues a job defined by buildJob in an external build system.
     *
     * Implementations should throw an exception if the triggering fails.
     */
    void trigger(BuildJob buildJob);

    /**
     * Returns whether the given job is currently running.
     */
    boolean isRunning(BuildJob buildJob);


    // TODO jvenstad: Implement with DeploymentTrigger.Job
    class BuildJob {

        private final long projectId;
        private final String jobName;

        public BuildJob(long projectId, String jobName) {
            this.projectId = projectId;
            this.jobName = jobName;
        }

        public long projectId() { return projectId; }
        public String jobName() { return jobName; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if ( ! (o instanceof BuildJob)) return false;
            BuildJob buildJob = (BuildJob) o;
            return projectId == buildJob.projectId &&
                   Objects.equals(jobName, buildJob.jobName);
        }

        @Override
        public String toString() {
            return jobName + "@" + projectId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(projectId, jobName);
        }

    }

}
