// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.config.model.api.TenantSecretStore;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author olaa
 */
public class SecretStoreExternalIdRetriever {

    private static final String SECRET_NAME_FORMAT = "%s.external.id.%s.%s";

    public static List<TenantSecretStore> populateExternalId(SecretStore secretStore, TenantName tenant, SystemName system, List<TenantSecretStore> tenantSecretStores) {
        return tenantSecretStores.stream()
                .map(tenantSecretStore -> {
                    var secretName = secretName(tenant, system, tenantSecretStore.getName());
                    String secret = secretStore.getSecret(secretName);
                    if (secret == null)
                     throw new RuntimeException("No secret found in secret store for " + secretName);
                    return tenantSecretStore.withExternalId(secret);
                })
                .collect(Collectors.toList());
    }

    public static String secretName(TenantName tenant, SystemName system, String storeName) {
        return String.format(SECRET_NAME_FORMAT, tenantSecretGroup(system), tenant.value(), storeName);
    }

    private static String tenantSecretGroup(SystemName system) {
        switch (system) {
            case Public:
                return "vespa.external.tenant.secrets";
            case PublicCd:
                return "vespa.external.cd.tenant.secrets";
            default:
                throw new IllegalArgumentException("No tenant secret store key group defined for system " + system);
        }
    }

}
