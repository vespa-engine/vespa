// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeRepository;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author mpolden
 */
public class NodeRepositoryMock implements NodeRepository {

    private final Map<ZoneId, Map<HostName, Node>> nodeRepository = new HashMap<>();

    public void add(ZoneId zone, List<Node> nodes) {
        nodeRepository.compute(zone, (k, v) -> {
            if (v == null) {
                v = new HashMap<>();
            }
            for (Node node : nodes) {
                v.put(node.hostname(), node);
            }
            return v;
        });
    }

    public void add(ZoneId zone, Node node) {
        add(zone, Collections.singletonList(node));
    }

    public void clear() {
        nodeRepository.clear();
    }

    @Override
    public List<Node> list(ZoneId zone, ApplicationId application) {
        return nodeRepository.getOrDefault(zone, Collections.emptyMap()).values().stream()
                             .filter(node -> node.owner().map(application::equals).orElse(false))
                             .collect(Collectors.toList());
    }

    @Override
    public void upgrade(ZoneId zone, NodeType type, Version version) {
        nodeRepository.getOrDefault(zone, Collections.emptyMap()).values()
                      .stream()
                      .filter(node -> node.type() == type)
                      .map(node -> new Node(node.hostname(), node.state(), node.type(), node.owner(),
                                            node.currentVersion(), version))
                      .forEach(node -> add(zone, node));
    }

}
