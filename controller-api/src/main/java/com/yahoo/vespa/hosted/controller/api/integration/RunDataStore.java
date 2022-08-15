// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;

import java.io.InputStream;
import java.util.Optional;

/**
 * @author jonmv
 */
public interface RunDataStore {

    /** Returns the run logs of the given deployment job, if existent. */
    Optional<byte[]> get(RunId id);

    /** Stores the given log for the given deployment job. */
    void put(RunId id, byte[] log);

    /** Returns the test report og the given deployment job, if present */
    Optional<byte[]> getTestReport(RunId id);

    /** Stores the test report for the given deployment job */
    void putTestReport(RunId id, byte[] report);

    /** Deletes the run logs and test report for the given deployment job. */
    void delete(RunId id);

    /** Deletes all data associated with the given application. */
    void delete(ApplicationId id);

    /** Stores Vespa logs for the run. */
    void putLogs(RunId id, boolean tester, InputStream logs);

    /** Fetches Vespa logs for the run. */
    InputStream getLogs(RunId id, boolean tester);

}
