// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.yahoo.vespa.hosted.controller.api.identifiers.AthenzDomain;
import com.yahoo.vespa.hosted.controller.api.identifiers.Property;
import com.yahoo.vespa.hosted.controller.api.identifiers.UserGroup;

import java.util.List;

/**
 * @author Tony Vaagenes
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public class TenantWithApplications {
    // TODO: use TenantMetaData instead of individual fields (requires dashboard updates)
    public TenantType type;
    public AthenzDomain athensDomain;
    public Property property;
    public UserGroup userGroup;
    public List<ApplicationReference> applications;

    public TenantWithApplications() {}

    public TenantWithApplications(
            TenantType type,
            AthenzDomain athensDomain,
            Property property,
            UserGroup userGroup,
            List<ApplicationReference> applications) {
        this.type = type;
        this.athensDomain = athensDomain;
        this.property = property;
        this.userGroup = userGroup;
        this.applications = applications;
    }
}
