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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
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

    private static final String HARDWARE_FAILURE_DESCRIPTION = "hardwareFailureDescription";
    private static final String WANT_TO_RETIRE = "wantToRetire";
    private static final String WANT_TO_DEPROVISION = "wantToDeprovision";

    private final NodeFlavors nodeFlavors;
    private final Inspector inspector;
    private final NodeRepository nodeRepository;

    private Node node;
    private List<Node> children;
    private boolean childrenModified = false;

    public NodePatcher(NodeFlavors nodeFlavors, InputStream json, Node node, NodeRepository nodeRepository) {
        try {
            this.nodeFlavors = nodeFlavors;
            inspector = SlimeUtils.jsonToSlime(IOUtils.readBytes(json, 1000 * 1000)).get();
            this.node = node;
            this.nodeRepository = nodeRepository;
            this.children = node.type().isDockerHost() ?
                    nodeRepository.getChildNodes(node.hostname()) :
                    Collections.emptyList();
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
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Could not set field '" + name + "'", e);
            }

            try {
                children = applyFieldRecursive(children, name, value);
                childrenModified = true;
            } catch (IllegalArgumentException e) {
                // Non recursive field, ignore
            }
        } );

        List<Node> nodes = childrenModified ? new ArrayList<>(children) : new ArrayList<>();
        nodes.add(node);

        return nodes;
    }

    private List<Node> applyFieldRecursive(List<Node> childNodes, String name, Inspector value) {
        switch (name) {
            case HARDWARE_FAILURE_DESCRIPTION:
                return childNodes.stream()
                        .map(child -> child.with(child.status().withHardwareFailureDescription(asOptionalString(value))))
                        .collect(Collectors.toList());
            case WANT_TO_RETIRE:
                return childNodes.stream()
                        .map(child -> child.with(child.status().withWantToRetire(asBoolean(value))))
                        .collect(Collectors.toList());

            case WANT_TO_DEPROVISION:
                return childNodes.stream()
                        .map(child -> child.with(child.status().withWantToDeprovision(asBoolean(value))))
                        .collect(Collectors.toList());

            default :
                throw new IllegalArgumentException("Field " + name + " is not recursive");
        }
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
            case HARDWARE_FAILURE_DESCRIPTION:
                return node.with(node.status().withHardwareFailureDescription(asOptionalString(value)));
            case "parentHostname" :
                return node.withParentHostname(asString(value));
            case "ipAddresses" :
                return node.withIpAddresses(asStringSet(value));
            case "additionalIpAddresses" :
                return node.withAdditionalIpAddresses(asStringSet(value));
            case WANT_TO_RETIRE :
                return node.with(node.status().withWantToRetire(asBoolean(value)));
            case WANT_TO_DEPROVISION :
                return node.with(node.status().withWantToDeprovision(asBoolean(value)));
            case "hardwareDivergence" :
                return node.with(node.status().withHardwareDivergence(removeQuotedNulls(asOptionalString(value))));
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

    private Optional<String> asOptionalString(Inspector field) {
        return field.type().equals(Type.NIX) ? Optional.empty() : Optional.of(asString(field));
    }

    // Remove when we no longer have "null" strings for this field in the node repo
    private Optional<String> removeQuotedNulls(Optional<String> value) {
        return value.filter(v -> !v.equals("null"));
    }

    private boolean asBoolean(Inspector field) {
        if ( ! field.type().equals(Type.BOOL))
            throw new IllegalArgumentException("Expected a BOOL value, got a " + field.type());
        return field.asBool();
    }

}
