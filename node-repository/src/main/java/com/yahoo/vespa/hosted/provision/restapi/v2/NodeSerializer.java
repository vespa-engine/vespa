// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.v2;

import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;

/**
 * Serializer for node wire format. Used to serialize node objects sent over the wire (HTTP).
 *
 * @author mpolden
 */
public class NodeSerializer {

    public Node.State stateFrom(String state) {
        switch (state) {
            case "active": return Node.State.active;
            case "dirty": return Node.State.dirty;
            case "failed": return Node.State.failed;
            case "inactive": return Node.State.inactive;
            case "parked": return Node.State.parked;
            case "provisioned": return Node.State.provisioned;
            case "ready": return Node.State.ready;
            case "reserved": return Node.State.reserved;
            default: throw new IllegalArgumentException("Unknown node state '" + state + "'");
        }
    }

    public String toString(Node.State state) {
        switch (state) {
            case active: return "active";
            case dirty: return "dirty";
            case failed: return "failed";
            case inactive: return "inactive";
            case parked: return "parked";
            case provisioned: return "provisioned";
            case ready: return "ready";
            case reserved: return "reserved";
            default: throw new IllegalArgumentException("Unknown node state '" + state + "'");
        }
    }

    public NodeType typeFrom(String nodeType) {
        switch (nodeType) {
            case "tenant": return NodeType.tenant;
            case "host": return NodeType.host;
            case "proxy": return NodeType.proxy;
            case "proxyhost": return NodeType.proxyhost;
            case "config": return NodeType.config;
            case "confighost": return NodeType.confighost;
            case "controller": return NodeType.controller;
            case "controllerhost": return NodeType.controllerhost;
            case "devhost": return NodeType.devhost;
            default: throw new IllegalArgumentException("Unknown node type '" + nodeType + "'");
        }
    }

    public String toString(NodeType type) {
        switch (type) {
            case tenant: return "tenant";
            case host: return "host";
            case proxy: return "proxy";
            case proxyhost: return "proxyhost";
            case config: return "config";
            case confighost: return "confighost";
            case controller: return "controller";
            case controllerhost: return "controllerhost";
            case devhost: return "devhost";
            default: throw new IllegalArgumentException("Unknown node type '" + type.name() + "'");
        }
    }

    public String toString(NodeResources.DiskSpeed diskSpeed) {
        switch (diskSpeed) {
            case fast : return "fast";
            case slow : return "slow";
            case any  : return "any";
            default: throw new IllegalArgumentException("Unknown disk speed '" + diskSpeed.name() + "'");
        }
    }

    public String toString(NodeResources.StorageType storageType) {
        switch (storageType) {
            case remote : return "remote";
            case local  : return "local";
            case any    : return "any";
            default: throw new IllegalArgumentException("Unknown storage type '" + storageType.name() + "'");
        }
    }

}
