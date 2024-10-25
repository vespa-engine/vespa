// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.zts.bindings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.vespa.athenz.api.AzureTemporaryCredentials;

import java.time.Instant;

/**
 * @author freva
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AzureTemporaryCredentialsResponseEntity implements TemporaryCredentialsResponse<AzureTemporaryCredentials> {
    private final AzureTemporaryCredentials credentials;

    public AzureTemporaryCredentialsResponseEntity(
            @JsonProperty("attributes") Attributes attributes,
            @JsonProperty("expiration") Instant expiration) {
        this.credentials = new AzureTemporaryCredentials(
                attributes.azureSubscription(),
                attributes.azureTenant(),
                attributes.accessToken(),
                expiration);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Attributes(@JsonProperty("azureSubscription") String azureSubscription,
                             @JsonProperty("azureTenant") String azureTenant,
                             @JsonProperty("accessToken") String accessToken) { }

    public AzureTemporaryCredentials credentials() {
        return credentials;
    }
}
