// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.athenz.identityprovider.api;

import java.net.URI;

/**
 * Vespa cluster type
 *
 * @author bjorncs
 */
public enum ClusterType {
    ADMIN,
    CONTAINER,
    CONTENT,
    COMBINED;

    public static ClusterType from(String cfgValue) {
        return switch (cfgValue) {
            case "admin" -> ADMIN;
            case "container" -> CONTAINER;
            case "content" -> CONTENT;
            case "combined" -> COMBINED;
            default -> throw new IllegalArgumentException("Illegal cluster type '" + cfgValue + "'");
        };
    }

    public String toConfigValue() {
        return switch (this) {
            case ADMIN -> "admin";
            case CONTAINER -> "container";
            case CONTENT -> "content";
            case COMBINED -> "combined";
        };
    }

    public URI asCertificateSanUri() { return URI.create("vespa://cluster-type/%s".formatted(toConfigValue())); }

}

