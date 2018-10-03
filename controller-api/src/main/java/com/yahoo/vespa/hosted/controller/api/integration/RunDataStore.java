// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;

import java.util.Optional;

/**
 * @author jonmv
 */
public interface RunDataStore {

    /** Returns the run logs of the given deployment job, if existent. */
    Optional<byte[]> get(RunId id);

    /** Stores the given log for the given deployment job. */
    void put(RunId id, byte[] log);

    /** Deletes all data associated with the given application. */
    void delete(ApplicationId id);

}
