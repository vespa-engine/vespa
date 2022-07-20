// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls;

import java.util.Arrays;

/**
 * @author bjorncs
 */
public enum Capability {
    CONTENT__CLUSTER_CONTROLLER__INTERNAL_STATE_API("vespa.content.cluster_controller.internal_state_api"),
    CONTENT__DOCUMENT_API("vespa.content.document_api"),
    CONTENT__METRICS_API("vespa.content.metrics_api"),
    CONTENT__SEARCH_API("vespa.content.search_api"),
    CONTENT__STATUS_PAGES("vespa.content.status_pages"),
    CONTENT__STORAGE_API("vespa.content.storage_api"),
    SLOBROK__API("vespa.slobrok.api"),
    ;

    private final String name;

    Capability(String name) { this.name = name; }

    public String asString() { return name; }

    public static Capability fromName(String name) {
        return Arrays.stream(values())
                .filter(c -> c.name.equals(name))
                .findAny().orElseThrow(() ->
                        new IllegalArgumentException("Cannot find predefined capability set with name '" + name + "'"));
    }

}
