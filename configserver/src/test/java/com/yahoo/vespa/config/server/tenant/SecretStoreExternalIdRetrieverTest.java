// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.config.server.tenant;

import com.yahoo.config.model.api.TenantSecretStore;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.config.server.MockSecretStore;
import com.yahoo.vespa.config.server.http.InvalidApplicationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author mortent
 */
public class SecretStoreExternalIdRetrieverTest {
    private final MockSecretStore secretStore = new MockSecretStore();
    private final TenantName tenantName = TenantName.from("myTenant");
    private final TenantSecretStore tenantSecretStore = new TenantSecretStore("name", "123456789012", "role");

    @Test
    public void fills_external_ids() {
        secretStore.put(SecretStoreExternalIdRetriever.secretName(tenantName, SystemName.PublicCd, "name"), "externalId");

        List<TenantSecretStore> tenantSecretStores = SecretStoreExternalIdRetriever.populateExternalId(secretStore, tenantName, SystemName.PublicCd, List.of(tenantSecretStore));
        assertEquals(1, tenantSecretStores.size());
        assertEquals("externalId", tenantSecretStores.get(0).getExternalId().get());
    }

    @Test
    public void reports_application_package_error_when_external_id_not_found() {
        InvalidApplicationException exception = assertThrows(InvalidApplicationException.class, () -> SecretStoreExternalIdRetriever.populateExternalId(secretStore, tenantName, SystemName.PublicCd, List.of(tenantSecretStore)));
        assertEquals("Could not find externalId for secret store: name", exception.getMessage());
    }
}
