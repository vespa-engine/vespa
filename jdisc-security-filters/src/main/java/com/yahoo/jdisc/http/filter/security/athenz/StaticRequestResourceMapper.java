// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.security.athenz;

import com.google.inject.Inject;
import com.yahoo.vespa.athenz.api.AthenzResourceName;

import java.util.Optional;

/**
 * A simple {@link RequestResourceMapper} that uses a fixed resource name and action
 *
 * @author bjorncs
 */
public class StaticRequestResourceMapper implements RequestResourceMapper {

    private final AthenzResourceName resourceName;
    private final String action;

    @Inject
    public StaticRequestResourceMapper(StaticRequestResourceMapperConfig config) {
        this(AthenzResourceName.fromString(config.resourceName()), config.action());
    }

    StaticRequestResourceMapper(AthenzResourceName resourceName, String action) {
        this.resourceName = resourceName;
        this.action = action;
    }

    @Override
    public Optional<ResourceNameAndAction> getResourceNameAndAction(String method, String uriPath, String uriQuery) {
        return Optional.of(new ResourceNameAndAction(resourceName, action));
    }
}
