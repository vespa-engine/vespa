// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.configserver;

import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.TenantName;

import java.net.URI;
import java.util.Map;

/**
 * @author freva
 */
public record ArchiveUris(Map<TenantName, URI> tenantArchiveUris, Map<CloudAccount, URI> accountArchiveUris) {
    public static final ArchiveUris EMPTY = new ArchiveUris(Map.of(), Map.of());
}
