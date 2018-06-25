package com.yahoo.vespa.hosted.controller.api.integration;

import com.yahoo.vespa.hosted.controller.api.integration.configserver.PrepareResponse;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;

/**
 * @author freva
 */
public interface LogStore {

    /** @return the test log of the given deployment job. */
    String getTestLog(RunId id);

    /** Stores the given test log for the given deployment job. */
    void setTestLog(RunId id, String testLog);

    /** @return the convergence log of the given deployment job. */
    String getConvergenceLog(RunId id);

    /** Stores the given convergence log for the given deployment job. */
    void setConvergenceLog(RunId id, String convergenceLog);

    /** @return the result of prepare of the test application for the given deployment job. */
    PrepareResponse getPrepareResponse(RunId id);

    /** Stores the given result of prepare of the test application for the given deployment job. */
    void setPrepareResponse(RunId id, PrepareResponse prepareResponse);

    /** Deletes all data associated with test of a given deployment job */
    void deleteTestData(RunId id);

}
