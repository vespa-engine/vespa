// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.entity;

import com.google.common.collect.ImmutableMap;
import com.yahoo.vespa.hosted.controller.api.identifiers.Property;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author mpolden
 */
public class MemoryEntityService implements EntityService {

    private final Map<String, NodeEntity> nodeEntities = new HashMap<>();

    @Override
    public Map<PropertyId, Property> listProperties() {
        return ImmutableMap.of(new PropertyId("1234"), new Property("foo"),
                               new PropertyId("4321"), new Property("bar"));
    }

    @Override
    public List<NodeEntity> listNodes() {
        return List.copyOf(nodeEntities.values());
    }

    @Override
    public Optional<NodeEntity> findNode(String hostname) {
        return Optional.empty();
    }

    public MemoryEntityService addNodeEntity(NodeEntity nodeEntity) {
        nodeEntities.put(nodeEntity.hostname(), nodeEntity);
        return this;
    }

}
