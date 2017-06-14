// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.restapi.wire;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.concurrent.Immutable;
import java.util.Collections;
import java.util.List;

@Immutable
public class BatchHostSuspendRequest {
    public static final String PARENT_HOSTNAME_FIELD = "parentHostname";
    public static final String HOSTNAMES_FIELD = "hostnames";

    public final String parentHostname;
    public final List<String> hostnames;

    @JsonCreator
    public BatchHostSuspendRequest(
            @JsonProperty(PARENT_HOSTNAME_FIELD) String parentHostname,
            @JsonProperty(HOSTNAMES_FIELD) List<String> hostnames) {
        this.parentHostname = parentHostname;
        this.hostnames = Collections.unmodifiableList(hostnames);
    }

    /**
     * @return The hostname of the parent of the hostnames, if applicable, which can be used for debugging.
     */
    @JsonProperty(PARENT_HOSTNAME_FIELD)
    public String getParentHostname() {
        return parentHostname;
    }

    @JsonProperty(HOSTNAMES_FIELD)
    public List<String> getHostnames() {
        return hostnames;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BatchHostSuspendRequest that = (BatchHostSuspendRequest) o;

        if (parentHostname != null ? !parentHostname.equals(that.parentHostname) : that.parentHostname != null)
            return false;
        return hostnames != null ? hostnames.equals(that.hostnames) : that.hostnames == null;

    }

    @Override
    public int hashCode() {
        int result = parentHostname != null ? parentHostname.hashCode() : 0;
        result = 31 * result + (hostnames != null ? hostnames.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "BatchHostSuspendRequest{" +
                "parentHostname=" + parentHostname +
                ", hostnames=" + hostnames +
                '}';
    }
}
