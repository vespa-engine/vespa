// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.deployment;

/**
 * @author mortent
 */
public class TestReport {

    private final String report;

    private TestReport(String report) {
        this.report = report;
    }

    public String toJson() {
        return report;
    }

    public static TestReport fromJson(String report) {
        return new TestReport(report);
    }

}
