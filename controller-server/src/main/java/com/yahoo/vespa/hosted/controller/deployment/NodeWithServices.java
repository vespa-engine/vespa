package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ServiceConvergence;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Aggregate of a node and its services, fetched from different sources.
 *
 * @author jonmv
 */
public class NodeWithServices {

    private final Node node;
    private final Node parent;
    private final long wantedConfigGeneration;
    private final List<ServiceConvergence.Status> services;

    public NodeWithServices(Node node, Node parent, long wantedConfigGeneration, List<ServiceConvergence.Status> services) {
        this.node = requireNonNull(node);
        this.parent = requireNonNull(parent);
        if (wantedConfigGeneration <= 0)
            throw new IllegalArgumentException("Wanted config generation must be positive");
        this.wantedConfigGeneration = wantedConfigGeneration;
        this.services = List.copyOf(services);
    }

    public Node node() { return node; }
    public Node parent() { return parent; }
    public long wantedConfigGeneration() { return wantedConfigGeneration; }
    public List<ServiceConvergence.Status> services() { return services; }

}
