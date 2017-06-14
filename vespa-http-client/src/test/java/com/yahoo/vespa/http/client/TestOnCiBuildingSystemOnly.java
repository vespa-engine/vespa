// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client;

import org.junit.Before;


public class TestOnCiBuildingSystemOnly {
    private static final String CI_BUILD_SYSTEN_ENV = "CI";
    private static final String CI_BUILD_SYSTEM_ENABLED_VALUE = "true";

    @Before
    public void beforeMethod() {
        org.junit.Assume.assumeTrue(CI_BUILD_SYSTEM_ENABLED_VALUE.equals(System.getenv(CI_BUILD_SYSTEN_ENV)));
    }
}
