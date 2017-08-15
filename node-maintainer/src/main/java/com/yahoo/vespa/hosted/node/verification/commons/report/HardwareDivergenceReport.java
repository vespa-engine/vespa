package com.yahoo.vespa.hosted.node.verification.commons.report;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class HardwareDivergenceReport {

    @JsonProperty
    SpecVerificationReport specVerificationReport;

    @JsonProperty
    BenchmarkReport benchmarkReport;

    public void setSpecVerificationReport(SpecVerificationReport specVerificationReport) {
        if (specVerificationReport.isValidSpec()){
            this.specVerificationReport = null;
        }
        else {
            this.specVerificationReport = specVerificationReport;
        }
    }

    public void setBenchmarkReport(BenchmarkReport benchmarkReport) {
        if (benchmarkReport.isAllBenchmarksOK()) {
            this.benchmarkReport = null;
        }
        else {
            this.benchmarkReport = benchmarkReport;
        }
    }

    @JsonIgnore
    public boolean isHardwareDivergenceReportEmpty(){
        if (specVerificationReport == null && benchmarkReport == null){
            return true;
        }
        return false;
    }

}
