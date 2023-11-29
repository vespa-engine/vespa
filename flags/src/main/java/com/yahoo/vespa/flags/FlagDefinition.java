// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * @author hakonhall
 */
public class FlagDefinition {
    private final UnboundFlag<?, ?, ?> unboundFlag;
    private final List<String> owners;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final String description;
    private final String modificationEffect;
    private final List<Dimension> dimensions;

    public FlagDefinition(
            UnboundFlag<?, ?, ?> unboundFlag,
            List<String> owners,
            Instant createdAt,
            Instant expiresAt,
            String description,
            String modificationEffect,
            Dimension... dimensions) {
        this.unboundFlag = unboundFlag;
        this.owners = owners;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.description = description;
        this.modificationEffect = modificationEffect;
        this.dimensions = List.of(dimensions);
        validate(owners, createdAt, expiresAt, this.dimensions);
    }

    public UnboundFlag<?, ?, ?> getUnboundFlag() {
        return unboundFlag;
    }

    public List<Dimension> getDimensions() {
        return dimensions;
    }

    public String getDescription() {
        return description;
    }

    public String getModificationEffect() {
        return modificationEffect;
    }

    public List<String> getOwners() { return owners; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getExpiresAt() { return expiresAt; }

    private static void validate(List<String> owners, Instant createdAt, Instant expiresAt, List<Dimension> dimensions) {
        if (expiresAt.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                    String.format(
                            "Flag cannot expire before its creation date (createdAt='%s', expiresAt='%s')",
                            createdAt, expiresAt));
        }

        if (owners == PermanentFlags.OWNERS) {
            if (!createdAt.equals(PermanentFlags.CREATED_AT) || !expiresAt.equals(PermanentFlags.EXPIRES_AT)) {
                throw new IllegalArgumentException("Invalid creation or expiration date for permanent flag");
            }
        } else if (owners.isEmpty()) {
            throw new IllegalArgumentException("Owner(s) must be specified");
        }

        if (dimensions.contains(Dimension.CONSOLE_USER_EMAIL)) {
            Set<Dimension> disallowedCombinations = EnumSet.allOf(Dimension.class);
            disallowedCombinations.remove(Dimension.CONSOLE_USER_EMAIL);
            disallowedCombinations.remove(Dimension.INSTANCE_ID);
            disallowedCombinations.remove(Dimension.TENANT_ID);
            disallowedCombinations.retainAll(dimensions);
            if (!disallowedCombinations.isEmpty())
                throw new IllegalArgumentException("Dimension " + Dimension.CONSOLE_USER_EMAIL + " cannot be combined with " + disallowedCombinations);
        }
    }
}
