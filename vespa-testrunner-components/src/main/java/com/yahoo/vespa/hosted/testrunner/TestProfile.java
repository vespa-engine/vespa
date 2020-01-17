// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.testrunner;

/**
 * @author valerijf
 * @author jvenstad
 */
enum TestProfile {

    SYSTEM_TEST("system, com.yahoo.vespa.tenant.systemtest.base.SystemTest", true),
    STAGING_SETUP_TEST("staging-setup, com.yahoo.vespa.tenant.systemtest.base.StagingTest", false),
    STAGING_TEST("staging, com.yahoo.vespa.tenant.systemtest.base.StagingTest", true),
    PRODUCTION_TEST("production, com.yahoo.vespa.tenant.systemtest.base.ProductionTest", false);

    private final String group;
    private final boolean failIfNoTests;

    TestProfile(String group, boolean failIfNoTests) {
        this.group = group;
        this.failIfNoTests = failIfNoTests;
    }

    String group() {
        return group;
    }

    boolean failIfNoTests() {
        return failIfNoTests;
    }

}
