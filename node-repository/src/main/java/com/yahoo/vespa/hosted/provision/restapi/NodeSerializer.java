// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi;

import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;

/**
 * Serializer for node wire format. Used to serialize node objects sent over the wire (HTTP).
 *
 * @author mpolden
 */
public class NodeSerializer {

    public static Node.State stateFrom(String state) {
        return switch (state) {
            case "active" -> Node.State.active;
            case "dirty" -> Node.State.dirty;
            case "failed" -> Node.State.failed;
            case "inactive" -> Node.State.inactive;
            case "parked" -> Node.State.parked;
            case "provisioned" -> Node.State.provisioned;
            case "ready" -> Node.State.ready;
            case "reserved" -> Node.State.reserved;
            case "deprovisioned" -> Node.State.deprovisioned;
            case "breakfixed" -> Node.State.breakfixed;
            default -> throw new IllegalArgumentException("Unknown node state '" + state + "'");
        };
    }

    public static String toString(Node.State state) {
        return switch (state) {
            case active -> "active";
            case dirty -> "dirty";
            case failed -> "failed";
            case inactive -> "inactive";
            case parked -> "parked";
            case provisioned -> "provisioned";
            case ready -> "ready";
            case reserved -> "reserved";
            case deprovisioned -> "deprovisioned";
            case breakfixed -> "breakfixed";
        };
    }

    public static NodeType typeFrom(String nodeType) {
        return switch (nodeType) {
            case "tenant" -> NodeType.tenant;
            case "host" -> NodeType.host;
            case "proxy" -> NodeType.proxy;
            case "proxyhost" -> NodeType.proxyhost;
            case "config" -> NodeType.config;
            case "confighost" -> NodeType.confighost;
            case "controller" -> NodeType.controller;
            case "controllerhost" -> NodeType.controllerhost;
            default -> throw new IllegalArgumentException("Unknown node type '" + nodeType + "'");
        };
    }

    public static String toString(NodeType type) {
        return switch (type) {
            case tenant -> "tenant";
            case host -> "host";
            case proxy -> "proxy";
            case proxyhost -> "proxyhost";
            case config -> "config";
            case confighost -> "confighost";
            case controller -> "controller";
            case controllerhost -> "controllerhost";
        };
    }

}
