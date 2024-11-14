// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.secret.model;

/**
 * Internally created id for a vault. Usually a UUID.
 *
 * @author gjoranv
 */
public record VaultId(String value) {

    public VaultId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Version id cannot be null or empty");
        }
    }

    public static VaultId of(String value) {
        return new VaultId(value);
    }

}
