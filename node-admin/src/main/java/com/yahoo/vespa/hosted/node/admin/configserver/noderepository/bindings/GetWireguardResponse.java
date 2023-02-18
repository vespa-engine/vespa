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
        public final String hostname;

        @JsonProperty("ipAddresses")
        public final List<String> ipAddresses;

        @JsonProperty("wireguardPubkey")
        public final String wireguardPubkey;

        @JsonCreator
        public Configserver(@JsonProperty("hostname") String hostname,
                            @JsonProperty("ipAddresses") List<String> ipAddresses,
                            @JsonProperty("wireguardPubkey") String wireguardPubkey) {
            this.hostname = hostname;
            this.ipAddresses = ipAddresses;
            this.wireguardPubkey = wireguardPubkey;
        }
    }

}
