// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.component.Version;

import java.util.Optional;

/**
 * @author gjoranv
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeployOptions {

    // TODO: Add build number here, so we can replace job timeout (12 hours) with triggering timeout (a few minutes?)
    public final Optional<ScrewdriverBuildJob> screwdriverBuildJob;
    public final Optional<String> vespaVersion;
    public final boolean ignoreValidationErrors;
    public final boolean deployCurrentVersion;

    @JsonCreator
    public DeployOptions(@JsonProperty("screwdriverBuildJob") Optional<ScrewdriverBuildJob> screwdriverBuildJob,
                         @JsonProperty("vespaVersion") Optional<Version> vespaVersion,
                         @JsonProperty("ignoreValidationErrors") boolean ignoreValidationErrors,
                         @JsonProperty("deployCurrentVersion") boolean deployCurrentVersion) {
        this.screwdriverBuildJob = screwdriverBuildJob;
        this.vespaVersion = vespaVersion.map(Version::toString);
        this.ignoreValidationErrors = ignoreValidationErrors;
        this.deployCurrentVersion = deployCurrentVersion;
    }

    @Override
    public String toString() {
        return "DeployData{" +
                "screwdriverBuildJob=" + screwdriverBuildJob.map(ScrewdriverBuildJob::toString).orElse("None") +
                ", vespaVersion=" + vespaVersion.orElse("None") +
                ", ignoreValidationErrors=" + ignoreValidationErrors +
                ", deployCurrentVersion=" + deployCurrentVersion +
                '}';
    }

    public static DeployOptions none() {
        return new DeployOptions(Optional.empty(), Optional.empty(), false, false);
    }
}
