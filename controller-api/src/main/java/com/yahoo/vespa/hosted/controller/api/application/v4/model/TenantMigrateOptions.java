// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.yahoo.vespa.hosted.controller.api.identifiers.AthensDomain;

/**
 * @author bjorncs
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public class TenantMigrateOptions {

    public AthensDomain athensDomain;

    public TenantMigrateOptions() {}

    public TenantMigrateOptions(AthensDomain athensDomain) {
        this.athensDomain = athensDomain;
    }
}
