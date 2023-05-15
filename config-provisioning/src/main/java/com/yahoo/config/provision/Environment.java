// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.Arrays;

/**
 * Environments in hosted Vespa.
 *
 * @author bratseth
 */
public enum Environment {

    /** The environment in which any external or internal applications serve actual requests */
    prod,

    /** Production-like environment which runs staging tests before an app is deployed to production */
    staging,

    /** Environment for running system tests before an app is deployed to staging */
    test,

    /** Environment used by individual developers to experiment */
    dev,

    /** Environment used to run performance and stability experiments */
    perf;

    /** Returns whether deployments to this environment are done manually */
    public boolean isManuallyDeployed() { return this == dev || this == perf; }

    /** Returns whether this environment is for automated tests */
    public boolean isTest() { return this == test || this == staging; }

    /** Returns whether this environment is production (prod) */
    public boolean isProduction() { return this == prod; }

    /** Returns whether this environment can exist in multiple regions. @deprecated they all are */
    @Deprecated // TODO: Remove after June 2023
    public boolean isMultiRegion() { return true; }

    /** Returns the prod environment. This is useful for non-hosted properties where we just need any consistent value */
    public static Environment defaultEnvironment() { return prod; }

    /** Returns whether this is one of the given environments */
    public boolean isAnyOf(Environment ... environments) {
        return Arrays.stream(environments).anyMatch(e -> e == this);
    }

    /** Returns the environment name from the string value returned by value() */
    public static Environment from(String value) {
        return switch (value) {
            case "prod" -> prod;
            case "staging" -> staging;
            case "test" -> test;
            case "dev" -> dev;
            case "perf" -> perf;
            default -> throw new IllegalArgumentException("'" + value + "' is not a valid environment identifier");
        };
    }

    /** Returns a name of this which is used in external API's and stored in persistent stores */
    public String value() {
        return switch (this) {
            case prod -> "prod";
            case staging -> "staging";
            case test -> "test";
            case dev -> "dev";
            case perf -> "perf";
        };
    }

}
