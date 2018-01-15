// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.yahoo.vespa.hosted.controller.api.identifiers.AthenzDomain;
import com.yahoo.vespa.hosted.controller.api.identifiers.Property;
import com.yahoo.vespa.hosted.controller.api.identifiers.UserGroup;

import java.util.Optional;

/**
 * @author gv
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(value = Include.NON_EMPTY)
public class TenantMetaData {
    public TenantType type;
    public Optional<AthenzDomain> athensDomain;
    public Optional<Property> property;
    public Optional<UserGroup> userGroup;

    // Required for Jackson deserialization
    public TenantMetaData() {}

    public TenantMetaData(TenantType type,
                          Optional<AthenzDomain> athensDomain,
                          Optional<Property> property,
                          Optional<UserGroup> userGroup) {
        this.type = type;
        this.athensDomain = athensDomain;
        this.property = property;
        this.userGroup = userGroup;
    }
}
