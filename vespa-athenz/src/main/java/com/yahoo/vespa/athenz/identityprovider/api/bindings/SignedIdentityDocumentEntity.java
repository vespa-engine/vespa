// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.api.bindings;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author bjorncs
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SignedIdentityDocumentEntity(
        String signature, int signingKeyVersion, String providerUniqueId, String providerService, int documentVersion,
        String configServerHostname, String instanceHostname, Instant createdAt, Set<String> ipAddresses,
        String identityType, String clusterType, String ztsUrl, String serviceIdentity, Map<String, Object> unknownAttributes) {

    @JsonCreator
    public SignedIdentityDocumentEntity(@JsonProperty("signature") String signature,
                                        @JsonProperty("signing-key-version") int signingKeyVersion,
                                        @JsonProperty("provider-unique-id") String providerUniqueId,
                                        @JsonProperty("provider-service") String providerService,
                                        @JsonProperty("document-version") int documentVersion,
                                        @JsonProperty("configserver-hostname") String configServerHostname,
                                        @JsonProperty("instance-hostname") String instanceHostname,
                                        @JsonProperty("created-at") Instant createdAt,
                                        @JsonProperty("ip-addresses") Set<String> ipAddresses,
                                        @JsonProperty("identity-type") String identityType,
                                        @JsonProperty("cluster-type") String clusterType,
                                        @JsonProperty("zts-url") String ztsUrl,
                                        @JsonProperty("service-identity") String serviceIdentity) {
        this(signature, signingKeyVersion, providerUniqueId, providerService, documentVersion, configServerHostname,
             instanceHostname, createdAt, ipAddresses, identityType, clusterType, ztsUrl, serviceIdentity, new HashMap<>());
    }

    @JsonProperty("signature") @Override public String signature() { return signature; }
    @JsonProperty("signing-key-version") @Override public int signingKeyVersion() { return signingKeyVersion; }
    @JsonProperty("provider-unique-id") @Override public String providerUniqueId() { return providerUniqueId; }
    @JsonProperty("provider-service") @Override public String providerService() { return providerService; }
    @JsonProperty("document-version") @Override public int documentVersion() { return documentVersion; }
    @JsonProperty("configserver-hostname") @Override public String configServerHostname() { return configServerHostname; }
    @JsonProperty("instance-hostname") @Override public String instanceHostname() { return instanceHostname; }
    @JsonProperty("created-at") @Override public Instant createdAt() { return createdAt; }
    @JsonProperty("ip-addresses") @Override public Set<String> ipAddresses() { return ipAddresses; }
    @JsonProperty("identity-type") @Override public String identityType() { return identityType; }
    @JsonProperty("cluster-type") @Override public String clusterType() { return clusterType; }
    @JsonProperty("zts-url") @Override public String ztsUrl() { return ztsUrl; }
    @JsonProperty("service-identity") @Override public String serviceIdentity() { return serviceIdentity; }
    @JsonAnyGetter @Override public Map<String, Object> unknownAttributes() { return unknownAttributes; }
    @JsonAnySetter public void set(String name, Object value) { unknownAttributes.put(name, value); }
}
