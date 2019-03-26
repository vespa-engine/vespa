package com.yahoo.vespa.hosted.testrunner;

/**
 * @author valerijf
 * @author jvenstad
 */
enum TestProfile {

    SYSTEM_TEST("com.yahoo.vespa.tenant.cd.SystemTest, com.yahoo.vespa.tenant.systemtest.base.SystemTest", true),
    STAGING_TEST("com.yahoo.vespa.tenant.cd.StagingTest, com.yahoo.vespa.tenant.systemtest.base.StagingTest", true),
    PRODUCTION_TEST("com.yahoo.vespa.tenant.cd.ProductionTest, com.yahoo.vespa.tenant.systemtest.base.ProductionTest", false);

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
