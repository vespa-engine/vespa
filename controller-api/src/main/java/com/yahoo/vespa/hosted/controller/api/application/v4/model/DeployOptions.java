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

    public final boolean deployDirectly;
    public final Optional<String> vespaVersion;
    public final boolean ignoreValidationErrors;
    public final boolean deployCurrentVersion;

    @JsonCreator
    public DeployOptions(@JsonProperty("deployDirectly") boolean deployDirectly,
                         @JsonProperty("vespaVersion") Optional<Version> vespaVersion,
                         @JsonProperty("ignoreValidationErrors") boolean ignoreValidationErrors,
                         @JsonProperty("deployCurrentVersion") boolean deployCurrentVersion) {
        this.deployDirectly = deployDirectly;
        this.vespaVersion = vespaVersion.map(Version::toString);
        this.ignoreValidationErrors = ignoreValidationErrors;
        this.deployCurrentVersion = deployCurrentVersion;
    }

    @Override
    public String toString() {
        return "DeployData{" +
                "deployDirectly=" + deployDirectly +
                ", vespaVersion=" + vespaVersion.orElse("None") +
                ", ignoreValidationErrors=" + ignoreValidationErrors +
                ", deployCurrentVersion=" + deployCurrentVersion +
                '}';
    }

    public static DeployOptions none() {
        return new DeployOptions(false, Optional.empty(), false, false);
    }
}
