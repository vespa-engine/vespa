// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.v2;

import com.yahoo.component.Version;
import com.yahoo.io.IOUtils;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Type;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.NodeFlavors;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * A class which can take a partial JSON node/v2 node JSON structure and apply it to a node object.
 * This is a one-time use object.
 *
 * @author bratseth
 */
public class NodePatcher {

    private final NodeFlavors nodeFlavors;

    private final Inspector inspector;
    private Node node;

    public NodePatcher(NodeFlavors nodeFlavors, InputStream json, Node node) {
        try {
            inspector = SlimeUtils.jsonToSlime(IOUtils.readBytes(json, 1000 * 1000)).get();
            this.node = node;
            this.nodeFlavors = nodeFlavors;
        }
        catch (IOException e) {
            throw new RuntimeException("Error reading request body", e);
        }
    }

    /**
     * Apply the json to the node and return the resulting node
     */
    public Node apply() {
        inspector.traverse((String name, Inspector value) -> {
            try {
                node = applyField(name, value);
            }
            catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Could not set field '" + name + "'", e);
            }
        } );
        return node;
    }

    private Node applyField(String name, Inspector value) {
        switch (name) {
            case "convergedStateVersion" :
                return node.setStatus(node.status().setStateVersion(asString(value)));
            case "currentRebootGeneration" :
                return node.setStatus(node.status().setReboot(node.status().reboot().setCurrent(asLong(value))));
            case "currentRestartGeneration" :
                return patchCurrentRestartGeneration(asLong(value));
            case "currentDockerImage" :
                return node.setStatus(node.status().setDockerImage(asString(value)));
            case "currentVespaVersion" :
                return node.setStatus(node.status().setVespaVersion(Version.fromString(asString(value))));
            case "currentHostedVersion" :
                return node.setStatus(node.status().setHostedVersion(Version.fromString(asString(value))));
            case "failCount" :
                return node.setStatus(node.status().setFailCount(asLong(value).intValue()));
            case "flavor" :
                return node.setConfiguration(node.configuration().setFlavor(nodeFlavors.getFlavorOrThrow(asString(value))));
            case "hardwareFailure" :
                return node.setStatus(node.status().setHardwareFailure(asBoolean(value)));
            case "parentHostname" :
                return node.setParentHostname(asString(value));
            default :
                throw new IllegalArgumentException("Could not apply field '" + name + "' on a node: No such modifiable field");
        }
    }

    private Node patchCurrentRestartGeneration(Long value) {
        Optional<Allocation> allocation = node.allocation();
        if (allocation.isPresent())
            return node.setAllocation(allocation.get().setRestart(allocation.get().restartGeneration().setCurrent(value)));
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

}
