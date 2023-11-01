// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import ai.vespa.validation.PatternedStringWrapper;

import java.util.regex.Pattern;

/**
 * Represents a tenant in the provision API.
 *
 * @author Ulf Lilleengen
 */
public class TenantName extends PatternedStringWrapper<TenantName> {

    private static final Pattern namePattern = Pattern.compile("[a-zA-Z0-9_-]{1,256}");
    private static final TenantName defaultName = new TenantName("default");

    private TenantName(String name) {
        super(name, namePattern, "tenant name");
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
