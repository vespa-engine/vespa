// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.stubs;

import com.yahoo.vespa.hosted.controller.api.integration.Issues;
import com.yahoo.vespa.hosted.controller.api.integration.Properties;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author mpolden
 */
public class PropertiesMock implements Properties {

    private final Map<Long, Issues.Classification> projects = new HashMap<>();

    public void addClassification(long propertyId, String classification) {
        projects.put(propertyId, new Issues.Classification(classification));
    }

    public Optional<Issues.Classification> classificationFor(long propertyId) {
        return Optional.ofNullable(projects.get(propertyId));
    }

}
