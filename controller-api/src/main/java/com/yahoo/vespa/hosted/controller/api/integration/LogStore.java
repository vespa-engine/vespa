package com.yahoo.vespa.hosted.controller.api.integration;

import com.yahoo.vespa.hosted.controller.api.integration.configserver.PrepareResponse;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;

import java.util.Optional;

/**
 * @author freva
 */
public interface LogStore {

    /** @return the log of the given step of the given deployment job, or an empty byte array if non-existent. */
    byte[] getLog(RunId id, String step);

    /** Stores the given log for the given step of the given deployment job. */
    void setLog(RunId id, String step, byte[] log);

    /** Deletes all data associated with the given deployment job */
    void deleteTestData(RunId id);

}
