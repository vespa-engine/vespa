// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.restapi.wire;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/*
 * A reason to reject a host state change request
 * @author andreer
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class HostStateChangeDenialReason {

    public static final String FIELD_NAME_CONSTRAINT = "constraint";
    public static final String FIELD_NAME_MESSAGE = "message";

    private final String constraintName;
    private final String message;

    @JsonCreator
    public HostStateChangeDenialReason(
            @JsonProperty(FIELD_NAME_CONSTRAINT) final String constraintName,
            @JsonProperty(FIELD_NAME_MESSAGE) final String message) {
        this.constraintName = constraintName;
        this.message = message;
    }

    @JsonProperty(FIELD_NAME_CONSTRAINT)
    public String constraintName() {
        return constraintName;
    }

    @JsonProperty(FIELD_NAME_MESSAGE)
    public String message() {
        return message;
    }

    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof HostStateChangeDenialReason)) {
            return false;
        }

        final HostStateChangeDenialReason other = (HostStateChangeDenialReason) o;
        if (!Objects.equals(this.constraintName, other.constraintName)) return false;
        if (!Objects.equals(this.message, other.message)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(constraintName, message);
    }
}
