// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import ai.vespa.validation.PatternedStringWrapper;
import com.google.common.io.BaseEncoding;

import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * Represents a tenant id in the provision API.
 *
 * @author enygaard
 */
public class TenantId extends PatternedStringWrapper<TenantId> {
    static final SecureRandom numberGenerator = new SecureRandom();

    // TODO: Remove empty id pattern when all tenants have a random id
    private static final Pattern idPattern = Pattern.compile("[a-z0-9]{26}|");

    private TenantId(String id) {
        super(id, idPattern, "tenant id");
    }

    public static TenantId from(String id) {
        return new TenantId(id);
    }

    public static TenantId create() {
        var bytes = new byte[16];
        numberGenerator.nextBytes(bytes);
        return TenantId.from(BaseEncoding.base32Hex().encode(bytes).replace("=", "").toLowerCase());
    }
}
