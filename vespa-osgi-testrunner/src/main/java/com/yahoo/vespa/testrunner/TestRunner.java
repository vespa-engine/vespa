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

    boolean isSupported();

    default TestReport getReport() { return null; }

    enum Status {
        NOT_STARTED, RUNNING, FAILURE, ERROR, SUCCESS
    }

    enum Suite {
        SYSTEM_TEST, STAGING_SETUP_TEST, STAGING_TEST, PRODUCTION_TEST
    }

}