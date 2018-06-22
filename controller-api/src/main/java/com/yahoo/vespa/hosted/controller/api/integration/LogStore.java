package com.yahoo.vespa.hosted.controller.api.integration;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;

// TODO jvenstad: Change most of this.
public interface LogStore {

    /** Returns the log of the given deployment job. */
    String getTestLog(ApplicationId application, JobType jobType, int build);

    /** Stores the given log for the given deployment job. */
    void setTestLog(ApplicationId application, JobType jobType, int build, String log);

    /** Deletes the log for the given deployment job. */
    void deleteTestLog(ApplicationId application, JobType jobType, int build);

}
