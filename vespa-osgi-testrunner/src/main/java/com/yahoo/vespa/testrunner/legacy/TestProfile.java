// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.testrunner.legacy;

import ai.vespa.hosted.api.TestDescriptor;

/**
 * @author valerijf
 * @author jvenstad
 */
public enum TestProfile {

    SYSTEM_TEST("system, com.yahoo.vespa.tenant.systemtest.base.SystemTest", true, TestDescriptor.TestCategory.systemtest),
    STAGING_SETUP_TEST("staging-setup", false, TestDescriptor.TestCategory.stagingsetuptest),
    STAGING_TEST("staging, com.yahoo.vespa.tenant.systemtest.base.StagingTest", true, TestDescriptor.TestCategory.stagingtest),
    PRODUCTION_TEST("production, com.yahoo.vespa.tenant.systemtest.base.ProductionTest", false, TestDescriptor.TestCategory.productiontest);

    private final String group;
    private final boolean failIfNoTests;
    private TestDescriptor.TestCategory testCategory;

    TestProfile(String group, boolean failIfNoTests, TestDescriptor.TestCategory testCategory) {
        this.group = group;
        this.failIfNoTests = failIfNoTests;
    }

    public String group() {
        return group;
    }

    public boolean failIfNoTests() {
        return failIfNoTests;
    }

    public TestDescriptor.TestCategory testCategory() {
        return testCategory;
    }

}
