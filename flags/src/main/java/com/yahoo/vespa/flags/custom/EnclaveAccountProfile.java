// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.custom;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.CloudName;

import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public record EnclaveAccountProfile(@JsonProperty("cloud") String cloud,
                                    @JsonProperty("account") String account,
                                    @JsonProperty("azureClientId") String azureClientId,
                                    @JsonProperty("azureTenantId") String azureTenantId) {
    public EnclaveAccountProfile {
        Objects.requireNonNull(cloud, "cloud must not be null");
        Objects.requireNonNull(account, "account must not be null");
    }

    public CloudAccount toCloudAccount() {
        return CloudAccount.from(CloudName.from(cloud), account);
    }
}
