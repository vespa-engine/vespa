// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository.bindings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A response from the /nodes/v2/wireguard api.
 *
 * @author gjoranv
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GetWireguardResponse {

    public final List<Configserver> configservers;

    @JsonCreator
    public GetWireguardResponse(@JsonProperty("configservers") List<Configserver> configservers) {
        this.configservers = configservers;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Configserver {

        @JsonProperty("hostname")
        public String hostname;

        @JsonProperty("ipAddresses")
        public List<String> ipAddresses;

        @JsonProperty("wireguard")
        public NodeRepositoryNode.WireguardKeyWithTimestamp wireguardKeyWithTimestamp;


        // TODO wg: remove when all nodes use new key+timestamp format
        @JsonProperty("wireguardPubkey")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public String wireguardPubkey;
        @JsonProperty("wireguardKeyTimestamp")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public Long wireguardKeyTimestamp;

    }

}
