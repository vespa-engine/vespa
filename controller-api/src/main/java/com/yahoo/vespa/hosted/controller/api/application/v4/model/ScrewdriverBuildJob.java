// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.vespa.hosted.controller.api.identifiers.ScrewdriverId;

import java.util.Objects;

/**
 * @author gjoranv
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScrewdriverBuildJob {
    public final ScrewdriverId screwdriverId;
    public final GitRevision gitRevision;

    @JsonCreator
    public ScrewdriverBuildJob(@JsonProperty("screwdriverId") ScrewdriverId screwdriverId,
                               @JsonProperty("gitRevision") GitRevision gitRevision) {
        this.screwdriverId = screwdriverId;
        this.gitRevision = gitRevision;
    }

    @Override
    public String toString() {
        return "ScrewdriverBuildJob{" +
                "screwdriverId=" + screwdriverId +
                ", gitRevision=" + gitRevision +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScrewdriverBuildJob that = (ScrewdriverBuildJob) o;
        return Objects.equals(screwdriverId, that.screwdriverId) &&
                Objects.equals(gitRevision, that.gitRevision);
    }

    @Override
    public int hashCode() {
        return Objects.hash(screwdriverId, gitRevision);
    }
}
