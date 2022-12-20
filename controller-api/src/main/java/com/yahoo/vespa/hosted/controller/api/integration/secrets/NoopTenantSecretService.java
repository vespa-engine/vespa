// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.secrets;

import com.yahoo.config.provision.TenantName;

import java.util.List;

/**
 * @author olaa
 */
public class NoopTenantSecretService implements TenantSecretService {

    @Override
    public void addSecretStore(TenantName tenant, TenantSecretStore tenantSecretStore, String externalId) {}

    @Override
    public void deleteSecretStore(TenantName tenant, TenantSecretStore tenantSecretStore) {}

    @Override
    public void cleanupSecretStores(List<TenantName> deletedTenants) {}
}
