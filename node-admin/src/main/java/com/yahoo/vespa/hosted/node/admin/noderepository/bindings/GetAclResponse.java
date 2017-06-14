// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.noderepository.bindings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

/**
 * This class represents a response from the /nodes/v2/acl/ API.
 *
 * @author mpolden
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetAclResponse {

    @JsonProperty("trustedNodes")
    public final List<Node> trustedNodes;

    @JsonCreator
    public GetAclResponse(@JsonProperty("trustedNodes") List<Node> trustedNodes) {
        this.trustedNodes = trustedNodes == null ? Collections.emptyList() : trustedNodes;
    }

    public static class Node {

        @JsonProperty("hostname")
        public final String hostname;

        @JsonProperty("ipAddress")
        public final String ipAddress;

        @JsonProperty("trustedBy")
        public final String trustedBy;

        @JsonCreator
        public Node(@JsonProperty("hostname") String hostname, @JsonProperty("ipAddress") String ipAddress,
                    @JsonProperty("trustedBy") String trustedBy) {
            this.hostname = hostname;
            this.ipAddress = ipAddress;
            this.trustedBy = trustedBy;
        }
    }
}
