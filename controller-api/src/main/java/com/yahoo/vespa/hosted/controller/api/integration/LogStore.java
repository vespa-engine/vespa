package com.yahoo.vespa.hosted.controller.api.integration;

import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;

/**
 * @author jonmv
 */
public interface LogStore {

    /** Returns the log of the given deployment job, or an empty byte array if non-existent. */
    byte[] get(RunId id);

    /** Stores the given log for the given deployment job. */
    void put(RunId id, byte[] log);

    /** Deletes all data associated with the given deployment job */
    void delete(RunId id);

}
