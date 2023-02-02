// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.api.bindings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Set;

/**
 * @author bjorncs
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SignedIdentityDocumentEntity(@JsonProperty("signature") String signature,
                                           @JsonProperty("signing-key-version") int signingKeyVersion,
                                           @JsonProperty("provider-unique-id") String providerUniqueId,
                                           @JsonProperty("provider-service") String providerService,
                                           @JsonProperty("document-version") int documentVersion,
                                           @JsonProperty("configserver-hostname") String configServerHostname,
                                           @JsonProperty("instance-hostname") String instanceHostname,
                                           @JsonProperty("created-at") Instant createdAt,
                                           @JsonProperty("ip-addresses") Set<String> ipAddresses,
                                           @JsonProperty("identity-type") String identityType) {
}
