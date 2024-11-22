// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import java.util.List;

/**
 * @author gjoranv
 */
public record TenantVault(String id, String name, String externalId, List<Secret> secrets) {

    public record Secret(String id, String name) { }

}
