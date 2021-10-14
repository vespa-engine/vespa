// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.billing;

import com.yahoo.config.provision.TenantName;

import java.util.Objects;

/**
 * @author olaa
 */
public class InstrumentOwner {

    private final TenantName tenantName;
    private final String userId;
    private final String paymentInstrumentId;
    private final boolean isDefault;

    public InstrumentOwner(TenantName tenantName, String userId, String paymentInstrumentId, boolean isDefault) {
        this.tenantName = tenantName;
        this.userId = userId;
        this.paymentInstrumentId = paymentInstrumentId;
        this.isDefault = isDefault;
    }

    public TenantName getTenantName() {
        return tenantName;
    }

    public String getUserId() {
        return userId;
    }

    public String getPaymentInstrumentId() {
        return paymentInstrumentId;
    }

    public boolean isDefault() {
        return isDefault;
    }

    @Override
    public String toString() {
        return String.format(
                "Tenant: %s\nCusomer ID: %s\nPayment Instrument ID: %s\nIs default: %s",
                tenantName.value(),
                userId,
                paymentInstrumentId,
                isDefault
            );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InstrumentOwner other = (InstrumentOwner) o;
        return this.tenantName.equals(other.getTenantName()) &&
                this.userId.equals(other.getUserId()) &&
                this.paymentInstrumentId.equals(other.getPaymentInstrumentId()) &&
                this.isDefault() == other.isDefault();
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantName, userId, paymentInstrumentId, isDefault);
    }
}
