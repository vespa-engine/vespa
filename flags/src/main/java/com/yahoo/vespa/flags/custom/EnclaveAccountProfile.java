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
public record EnclaveAccountProfile(@JsonProperty("cloudAccount") String cloudAccount,
                                    @JsonProperty("azureClientId") String azureClientId,
                                    @JsonProperty("azureTenantId") String azureTenantId) {
    public EnclaveAccountProfile {
        Objects.requireNonNull(cloudAccount, "cloudAccount must not be null");
        CloudName cloud = CloudAccount.from(cloudAccount).cloudName();
        if (cloud == CloudName.AZURE) {
            Objects.requireNonNull(azureClientId, "azureClientId must not be null for cloudAccount " + cloudAccount);
            Objects.requireNonNull(azureTenantId, "azureTenantId must not be null for cloudAccount " + cloudAccount);
        } else if (azureClientId != null || azureTenantId != null) {
            throw new IllegalArgumentException("azureClientId and azureTenantId can not be set for cloudAccount " + cloudAccount);
        }
    }

    public CloudAccount toCloudAccount() {
        return CloudAccount.from(cloudAccount);
    }
}
