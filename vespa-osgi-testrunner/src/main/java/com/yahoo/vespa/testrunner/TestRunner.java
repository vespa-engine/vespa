// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.testrunner;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.logging.LogRecord;

/**
 * @author jonmv
 * @author mortent
 */
public interface TestRunner {

    Collection<LogRecord> getLog(long after);

    Status getStatus();

    CompletableFuture<?> test(Suite suite, byte[] config);

    default TestReport getReport() { return null; }

    /** Test run status, ordered from most to least specific; the most specific result is chosen when combining multiple. */
    enum Status {

        /** Tests are currently running. */
        RUNNING,

        /** Framework exception; never got to run the tests, or failed parsing their output. */
        ERROR,

        /** Test code failed. */
        FAILURE,

        /** Tests should be re-run at a later time. */
        INCONCLUSIVE,

        /** All tests passed. */
        SUCCESS,

        /** No tests found. */
        NO_TESTS,

        /** Tests have not yet started. */
        NOT_STARTED

        }

    enum Suite {
        SYSTEM_TEST, STAGING_SETUP_TEST, STAGING_TEST, PRODUCTION_TEST
    }

}