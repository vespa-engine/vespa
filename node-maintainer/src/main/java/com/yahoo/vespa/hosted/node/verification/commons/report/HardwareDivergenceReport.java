// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.verification.commons.report;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * JSON-wrapped report for node repo
 *
 * @author sgrostad
 * @author olaaun
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HardwareDivergenceReport {

    @JsonProperty
    SpecVerificationReport specVerificationReport;

    @JsonProperty
    BenchmarkReport benchmarkReport;

    public void setSpecVerificationReport(SpecVerificationReport specVerificationReport) {
        if (specVerificationReport.isValidSpec()) {
            this.specVerificationReport = null;
        } else {
            this.specVerificationReport = specVerificationReport;
        }
    }

    public void setBenchmarkReport(BenchmarkReport benchmarkReport) {
        if (benchmarkReport.isAllBenchmarksOK()) {
            this.benchmarkReport = null;
        } else {
            this.benchmarkReport = benchmarkReport;
        }
    }

    @JsonIgnore
    public boolean isHardwareDivergenceReportEmpty() {
        return specVerificationReport == null && benchmarkReport == null;
    }
}
