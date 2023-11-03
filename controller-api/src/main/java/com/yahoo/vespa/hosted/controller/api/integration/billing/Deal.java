// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.billing;

import ai.vespa.validation.StringWrapper;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.api.role.SimplePrincipal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * @author freva
 */
public record Deal(Id id,
                   TenantName tenantName,
                   PlanId planId,
                   SimplePrincipal principal,
                   Instant effectiveAt,
                   Instant committedSpendChangedAt,
                   BigDecimal committedSpend) {
    public Deal {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(tenantName, "tenantName cannot be null");
        Objects.requireNonNull(planId, "planId cannot be null");
        Objects.requireNonNull(principal, "principal cannot be null");
        Objects.requireNonNull(effectiveAt, "effectiveAt cannot be null");
        Objects.requireNonNull(committedSpendChangedAt, "committedSpendChangedAt cannot be null");
        Objects.requireNonNull(committedSpend, "committedSpend cannot be null");

        if (committedSpendChangedAt.isAfter(effectiveAt))
            throw new IllegalArgumentException("committedSpendChangedAt cannot be after effectiveAt");
    }

    public static Deal of(TenantName tenantName, PlanId planId, SimplePrincipal principal, Instant effectiveAt,
                          Instant committedSpendChangedAt, BigDecimal committedSpend) {
        return new Deal(Id.of(UUID.randomUUID().toString()), tenantName, planId, principal, effectiveAt, committedSpendChangedAt, committedSpend);
    }

    public static class Id extends StringWrapper<Id> {
        private Id(String value) {
            super(value);
            if (value.isBlank()) throw new IllegalArgumentException("id must be non-blank");
        }

        @Override
        public String toString() {
            return "DealId{value='" + value() + "'}";
        }

        public static Id of(String value) {
            return new Id(value);
        }
    }
}
