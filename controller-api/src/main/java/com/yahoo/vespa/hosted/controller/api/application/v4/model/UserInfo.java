// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.yahoo.vespa.hosted.controller.api.identifiers.UserId;

import java.util.List;

/**
 * @author gv
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserInfo {
    public UserId user;
    public boolean tenantExists;
    public List<TenantInfo> tenants;
}
