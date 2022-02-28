// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.testrunner;

/**
 * @author valerijf
 * @author jvenstad
 */
public enum TestProfile {

    SYSTEM_TEST("system, com.yahoo.vespa.tenant.systemtest.base.SystemTest", true),
    STAGING_SETUP_TEST("staging-setup", false),
    STAGING_TEST("staging, com.yahoo.vespa.tenant.systemtest.base.StagingTest", true),
    PRODUCTION_TEST("production, com.yahoo.vespa.tenant.systemtest.base.ProductionTest", true);

    private final String group;
    private final boolean failIfNoTests;

    TestProfile(String group, boolean failIfNoTests) {
        this.group = group;
        this.failIfNoTests = failIfNoTests;
    }

    public String group() {
        return group;
    }

    public boolean failIfNoTests() {
        return failIfNoTests;
    }
}
