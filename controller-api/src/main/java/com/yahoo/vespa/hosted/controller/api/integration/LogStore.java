package com.yahoo.vespa.hosted.controller.api.integration;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.PrepareResponse;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;

public interface LogStore {

    /** @return the test log of the given deployment job. */
    String getTestLog(ApplicationId applicationId, JobType jobType, long buildId);

    /** Stores the given test log for the given deployment job. */
    void setTestLog(ApplicationId applicationId, JobType jobType, long buildId, String testLog);

    /** @return the convergence log of the given deployment job. */
    String getConvergenceLog(ApplicationId applicationId, JobType jobType, long buildId);

    /** Stores the given convergence log for the given deployment job. */
    void setConvergenceLog(ApplicationId applicationId, JobType jobType, long buildId, String convergenceLog);

    /** @return the result of prepare of the test application for the given deployment job. */
    PrepareResponse getPrepareResponse(ApplicationId applicationId, JobType jobType, long buildId);

    /** Stores the given result of prepare of the test application for the given deployment job. */
    void setPrepareResponse(ApplicationId applicationId, JobType jobType, long buildId, PrepareResponse prepareResponse);

    /** Deletes all data associated with test of a given deployment job */
    void deleteTestData(ApplicationId applicationId, JobType jobType, long buildId);
}
