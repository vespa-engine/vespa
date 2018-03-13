// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.entity;

import com.yahoo.vespa.hosted.controller.api.identifiers.Property;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;

import java.util.Map;

/**
 * A service which provides access to business-specific entities.
 *
 * @author mpolden
 */
public interface EntityService {

    /** List all properties known by the service */
    Map<PropertyId, Property> listProperties();

}
