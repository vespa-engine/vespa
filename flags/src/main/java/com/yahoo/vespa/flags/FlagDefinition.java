// Copyright 2019 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import javax.annotation.concurrent.Immutable;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author hakonhall
 */
@Immutable
public class FlagDefinition {
    private final UnboundFlag<?, ?, ?> unboundFlag;
    private final List<String> owners;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final String description;
    private final String modificationEffect;
    private final List<FetchVector.Dimension> dimensions;

    public FlagDefinition(
            UnboundFlag<?, ?, ?> unboundFlag,
            List<String> owners,
            Instant createdAt,
            Instant expiresAt,
            String description,
            String modificationEffect,
            FetchVector.Dimension... dimensions) {
        validate(owners, createdAt, expiresAt);
        this.unboundFlag = unboundFlag;
        this.owners = owners;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.description = description;
        this.modificationEffect = modificationEffect;
        this.dimensions = Collections.unmodifiableList(Arrays.asList(dimensions));
    }

    public UnboundFlag<?, ?, ?> getUnboundFlag() {
        return unboundFlag;
    }

    public List<FetchVector.Dimension> getDimensions() {
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

    private static void validate(List<String> owners, Instant createdAt, Instant expiresAt) {
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
    }
}
