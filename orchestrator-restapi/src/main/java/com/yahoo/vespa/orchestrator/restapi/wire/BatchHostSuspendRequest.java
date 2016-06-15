// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.restapi.wire;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.concurrent.Immutable;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Immutable
public class BatchHostSuspendRequest {
    public static final String PARENT_HOSTNAME_FIELD = "parentHostname";
    public static final String HOSTNAMES_FIELD = "hostnames";

    public final Optional<String> parentHostname;
    public final Optional<List<String>> hostnames;

    @JsonCreator
    public BatchHostSuspendRequest(
            @JsonProperty(PARENT_HOSTNAME_FIELD) String parentHostname,
            @JsonProperty(HOSTNAMES_FIELD) List<String> hostnames) {
        this.parentHostname = Optional.ofNullable(parentHostname);
        this.hostnames = Optional.ofNullable(hostnames).map(Collections::unmodifiableList);
    }

    /**
     * @return The hostname of the parent of the hostnames, if applicable, which can be used for debugging.
     */
    @JsonProperty(PARENT_HOSTNAME_FIELD)
    public Optional<String> getParentHostname() {
        return parentHostname;
    }

    @JsonProperty(HOSTNAMES_FIELD)
    public Optional<List<String>> getHostnames() {
        return hostnames;
    }

    @Override
    public String toString() {
        return "BatchHostSuspendRequest{" +
                "parentHostname=" + parentHostname +
                ", hostnames=" + hostnames +
                '}';
    }
}
