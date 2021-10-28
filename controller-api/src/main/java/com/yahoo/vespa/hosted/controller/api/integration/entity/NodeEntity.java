// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.entity;

import java.util.Objects;
import java.util.Optional;

/**
 * Information about a node from a {@link EntityService}.
 *
 * @author mpolden
 */
public class NodeEntity {

    private final String hostname;
    private final Optional<String> model;
    private final Optional<String> manufacturer;
    private final Optional<String> switchHostname;

    public NodeEntity(String hostname, String model, String manufacturer, String switchHostname) {
        this.hostname = Objects.requireNonNull(hostname);
        this.model = nonBlank(model);
        this.manufacturer = nonBlank(manufacturer);
        this.switchHostname = nonBlank(switchHostname);
    }

    public String hostname() {
        return hostname;
    }

    /** The model name of this node */
    public Optional<String> model() {
        return model;
    }

    /** The manufacturer of this node */
    public Optional<String> manufacturer() {
        return manufacturer;
    }

    /** The hostname of network switch this node is connected to */
    public Optional<String> switchHostname() {
        return switchHostname;
    }

    private static Optional<String> nonBlank(String s) {
        return Optional.ofNullable(s).filter(v -> !v.isBlank());
    }

}
