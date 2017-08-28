// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.vespa.hosted.controller.api.identifiers.AthensDomain;
import com.yahoo.vespa.hosted.controller.api.identifiers.Property;
import com.yahoo.vespa.hosted.controller.api.identifiers.UserGroup;

import java.util.Objects;
import java.util.Optional;

/**
 * @author gv
 * @author bjorncs
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(value = JsonInclude.Include.NON_ABSENT)
public class TenantUpdateOptions {
    public final Property property;
    public final Optional<UserGroup> userGroup;
    public final Optional<AthensDomain> athensDomain;

    @JsonCreator
    public TenantUpdateOptions(@JsonProperty("property") Property property,
                               @JsonProperty("userGroup") Optional<UserGroup> userGroup,
                               @JsonProperty("athensDomain") Optional<AthensDomain> athensDomain) {
        this.userGroup = userGroup;
        this.property = property;
        this.athensDomain = athensDomain;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TenantUpdateOptions that = (TenantUpdateOptions) o;
        return Objects.equals(property, that.property) &&
                Objects.equals(userGroup, that.userGroup) &&
                Objects.equals(athensDomain, that.athensDomain);
    }

    @Override
    public int hashCode() {
        return Objects.hash(property, userGroup, athensDomain);
    }

    @Override
    public String toString() {
        return "TenantUpdateOptions{" +
                "property=" + property +
                ", userGroup=" + userGroup +
                ", athensDomain=" + athensDomain +
                '}';
    }
}
