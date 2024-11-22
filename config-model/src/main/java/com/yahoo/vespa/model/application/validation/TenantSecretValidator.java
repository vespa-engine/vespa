// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.model.api.TenantVault;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.xml.CloudSecrets;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author gjoranv
 */
public class TenantSecretValidator implements Validator {

    @Override
    public void validate(Validation.Context context) {
        if ( ! context.deployState().isHosted()) return;
        if ( ! context.deployState().zone().system().isPublic()) return;

        for (ApplicationContainerCluster cluster : context.model().getContainerClusters().values()) {

            // vaultName -> secretList
            Map<String, List<TenantVault.Secret>> existingVaults = context.deployState().getProperties().tenantVaults().stream()
                    .collect(Collectors.toMap(TenantVault::name, TenantVault::secrets));

            var cloudSecrets = cluster.getTenantSecrets();

            if (cloudSecrets.isPresent()) {
                CloudSecrets secrets = cloudSecrets.get();
                for (CloudSecrets.SecretConfig secretConfig : secrets.configuredSecrets()) {
                    if (! existingVaults.containsKey(secretConfig.vault())) {
                        context.illegal("Vault '" + secretConfig.vault() + "' does not exist, or application does not have access to it");
                    } else {
                        // Vault exists, check that the secret exists in the vault
                        var vaultSecrets = existingVaults.get(secretConfig.vault());
                        if (! hasSecret(secretConfig.name(), vaultSecrets)) {
                            context.illegal("Secret '%s' is not defined in vault '%s'".formatted(
                                    secretConfig.name(), secretConfig.vault()));
                        }
                    }
                }
            }
        }
    }

    private boolean hasSecret(String secretName, List<TenantVault.Secret> secrets) {
        return secrets.stream().anyMatch(secret -> secret.name().equals(secretName));
    }

}
