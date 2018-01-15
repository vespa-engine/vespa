// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.hosted.controller.api.identifiers.Property;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;
import com.yahoo.vespa.hosted.controller.api.identifiers.UserGroup;

/**
 * @author bjorncs
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public class TenantCreateOptions {
    public AthenzDomain athensDomain;
    public Property property;
    public PropertyId propertyId;
    public UserGroup userGroup;

    public TenantCreateOptions() {}

    public TenantCreateOptions(UserGroup userGroup, Property property, PropertyId propertyId) {
        this.userGroup = userGroup;
        this.property = property;
        this.propertyId = propertyId;
    }

    public TenantCreateOptions(AthenzDomain athensDomain, Property property, PropertyId propertyId) {
        this.athensDomain = athensDomain;
        this.property = property;
        this.propertyId = propertyId;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("options: ");
        sb.append("athens-domain='").append(this.athensDomain.getName()).append("', ");
        sb.append("property='").append(this.property).append("'");
        if (this.propertyId != null) {
            sb.append(", propertyId='").append(this.propertyId).append("'");
        }

        return sb.toString();
    }
}
