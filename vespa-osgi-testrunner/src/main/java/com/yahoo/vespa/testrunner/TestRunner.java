// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.testrunner;

import ai.vespa.hosted.api.TestDescriptor;
import com.yahoo.vespa.testrunner.legacy.LegacyTestRunner;

/**
 * @author mortent
 */
public interface TestRunner {
    void executeTests(TestDescriptor.TestCategory category, byte[] testConfig);

    boolean isSupported();

    LegacyTestRunner.Status getStatus();

    TestReport getReport();

    String getReportAsJson();
}
