// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import ai.vespa.validation.PatternedStringWrapper;

/**
 * Represents a tenant in the provision API.
 *
 * @author Ulf Lilleengen
 */
public class TenantName extends PatternedStringWrapper<TenantName> {

    private static final TenantName defaultName = new TenantName("default");

    private TenantName(String name) {
        super(name, ApplicationId.namePattern, "tenant name");
    }

    public static TenantName from(String name) {
        return new TenantName(name);
    }
    
    public static TenantName defaultName() {
        return defaultName;
    }

    public boolean isDefault() {
        return equals(defaultName);
    }

}
