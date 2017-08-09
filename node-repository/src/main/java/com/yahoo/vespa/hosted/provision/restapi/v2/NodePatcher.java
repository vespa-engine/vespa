// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.v2;

import com.yahoo.component.Version;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeType;
import com.yahoo.io.IOUtils;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Type;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.Status;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * A class which can take a partial JSON node/v2 node JSON structure and apply it to a node object.
 * This is a one-time use object.
 *
 * @author bratseth
 */
public class NodePatcher {

    public static final String HARDWARE_FAILURE_TYPE = "hardwareFailureType";
    private final NodeFlavors nodeFlavors;
    private final Inspector inspector;
    private final NodeRepository nodeRepository;

    private Node node;

    public NodePatcher(NodeFlavors nodeFlavors, InputStream json, Node node, NodeRepository nodeRepository) {
        try {
            this.nodeFlavors = nodeFlavors;
            inspector = SlimeUtils.jsonToSlime(IOUtils.readBytes(json, 1000 * 1000)).get();
            this.node = node;
            this.nodeRepository = nodeRepository;
        }
        catch (IOException e) {
            throw new RuntimeException("Error reading request body", e);
        }
    }

    /**
     * Apply the json to the node and return all nodes affected by the patch.
     * More than 1 node may be affected if e.g. the node is a Docker host, which may have
     * children that must be updated in a consistent manner.
     */
    public List<Node> apply() {
        inspector.traverse((String name, Inspector value) -> {
            try {
                node = applyField(name, value);
            }
            catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Could not set field '" + name + "'", e);
            }
        } );


        List<Node> nodes = new ArrayList<>();
        if (node.type() == NodeType.host) {
            nodes.addAll(modifiedDockerChildNodes());
        }
        nodes.add(node);

        return nodes;
    }

    private List<Node> modifiedDockerChildNodes() {
        List<Node> children = nodeRepository.getChildNodes(node.hostname());
        boolean modified = false;

        if (inspector.field(HARDWARE_FAILURE_TYPE).valid()) {
            modified = true;
            children = children.stream()
                    .map(node -> node.with(node.status().withHardwareFailure(toHardwareFailureType(asString(inspector.field(HARDWARE_FAILURE_TYPE))))))
                    .collect(Collectors.toList());
        }

        return modified ? children : new ArrayList<>();
    }

    private Node applyField(String name, Inspector value) {
        switch (name) {
            case "convergedStateVersion" :
                return node; // TODO: Ignored, can be removed when callers no longer include this field
            case "currentRebootGeneration" :
                return node.withCurrentRebootGeneration(asLong(value), nodeRepository.clock().instant());
            case "currentRestartGeneration" :
                return patchCurrentRestartGeneration(asLong(value));
            case "currentDockerImage" :
                Version versionFromImage = Optional.of(asString(value))
                        .filter(s -> !s.isEmpty())
                        .map(DockerImage::new)
                        .map(DockerImage::tagAsVersion)
                        .orElse(Version.emptyVersion);
                return node.with(node.status().withVespaVersion(versionFromImage));
            case "currentVespaVersion" :
                return node.with(node.status().withVespaVersion(Version.fromString(asString(value))));
            case "currentHostedVersion" :
                return node; // TODO: Ignored, can be removed when callers no longer include this field
            case "failCount" :
                return node.with(node.status().setFailCount(asLong(value).intValue()));
            case "flavor" :
                return node.with(nodeFlavors.getFlavorOrThrow(asString(value)));
            case HARDWARE_FAILURE_TYPE:
                return node.with(node.status().withHardwareFailure(toHardwareFailureType(asString(value))));
            case "parentHostname" :
                return node.withParentHostname(asString(value));
            case "ipAddresses" :
                return node.withIpAddresses(asStringSet(value));
            case "additionalIpAddresses" :
                return node.withAdditionalIpAddresses(asStringSet(value));
            case "wantToRetire" :
                return node.with(node.status().withWantToRetire(asBoolean(value)));
            case "wantToDeprovision" :
                return node.with(node.status().withWantToDeprovision(asBoolean(value)));
            case "hardwareDivergence" :
                if (value.type().equals(Type.NIX)) {
                    return node.with(node.status().withHardwareDivergence(Optional.empty()));
                }
                return node.with(node.status().withHardwareDivergence(Optional.of(asString(value))));
            default :
                throw new IllegalArgumentException("Could not apply field '" + name + "' on a node: No such modifiable field");
        }
    }

    private Set<String> asStringSet(Inspector field) {
        if ( ! field.type().equals(Type.ARRAY))
            throw new IllegalArgumentException("Expected an ARRAY value, got a " + field.type());

        TreeSet<String> strings = new TreeSet<>();
        for (int i = 0; i < field.entries(); i++) {
            Inspector entry = field.entry(i);
            if ( ! entry.type().equals(Type.STRING))
                throw new IllegalArgumentException("Expected a STRING value, got a " + entry.type());
            strings.add(entry.asString());
        }

        return strings;
    }
    
    private Node patchCurrentRestartGeneration(Long value) {
        Optional<Allocation> allocation = node.allocation();
        if (allocation.isPresent())
            return node.with(allocation.get().withRestart(allocation.get().restartGeneration().withCurrent(value)));
        else
            throw new IllegalArgumentException("Node is not allocated");
    }

    private Long asLong(Inspector field) {
        if ( ! field.type().equals(Type.LONG))
            throw new IllegalArgumentException("Expected a LONG value, got a " + field.type());
        return field.asLong();
    }

    private String asString(Inspector field) {
        if ( ! field.type().equals(Type.STRING))
            throw new IllegalArgumentException("Expected a STRING value, got a " + field.type());
        return field.asString();
    }

    private boolean asBoolean(Inspector field) {
        if ( ! field.type().equals(Type.BOOL))
            throw new IllegalArgumentException("Expected a BOOL value, got a " + field.type());
        return field.asBool();
    }

    private Optional<Status.HardwareFailureType> toHardwareFailureType(String failureType) {
        switch (failureType) {
            case "memory_mcelog" : return Optional.of(Status.HardwareFailureType.memory_mcelog);
            case "disk_smart" : return Optional.of(Status.HardwareFailureType.disk_smart);
            case "disk_kernel" : return Optional.of(Status.HardwareFailureType.disk_kernel);
            case "unknown" : throw new IllegalArgumentException("An actual hardware failure type must be provided, not 'unknown'");
            default : throw new IllegalArgumentException("Unknown hardware failure '" + failureType + "'");
        }
    }

}
