package com.yahoo.vespa.hosted.node.verification.commons.report;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class HardwareDivergenceReport {

    @JsonProperty
    SpecVerificationReport specVerificationReport;

    @JsonProperty
    BenchmarkReport benchmarkReport;

    public void setSpecVerificationReport(SpecVerificationReport specVerificationReport) {
        this.specVerificationReport = specVerificationReport;
    }

    public void setBenchmarkReport(BenchmarkReport benchmarkReport) {
        this.benchmarkReport = benchmarkReport;
    }

}
