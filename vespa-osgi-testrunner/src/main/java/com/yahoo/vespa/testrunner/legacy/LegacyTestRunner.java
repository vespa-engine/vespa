// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.testrunner.legacy;

import java.util.Collection;
import java.util.logging.LogRecord;

/**
 * @author mortent
 */
public interface LegacyTestRunner {

    Collection<LogRecord> getLog(long after);

    Status getStatus();

    void test(Suite suite, byte[] config);

    enum Status {
        NOT_STARTED, RUNNING, FAILURE, ERROR, SUCCESS
    }

    enum Suite {
        SYSTEM_TEST, STAGING_SETUP_TEST, STAGING_TEST, PRODUCTION_TEST
    }

}