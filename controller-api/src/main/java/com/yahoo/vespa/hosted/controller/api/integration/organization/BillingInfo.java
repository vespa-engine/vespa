// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.organization;

import java.util.Objects;
import java.util.StringJoiner;

import static java.util.Objects.requireNonNull;

/**
 * Information pertinent to billing a tenant for use of hosted Vespa services.
 *
 * @author jonmv
 */
public class BillingInfo {

    private final String customerId;
    private final String productCode;

    /** Creates a new BillingInfo with the given data. Assumes data has already been validated. */
    public BillingInfo(String customerId, String productCode) {
        this.customerId = requireNonNull(customerId);
        this.productCode = requireNonNull(productCode);
    }

    public String customerId() {
        return customerId;
    }

    public String productCode() {
        return productCode;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", BillingInfo.class.getSimpleName() + "[", "]")
                .add("customerId='" + customerId + "'")
                .add("productCode='" + productCode + "'")
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if ( ! (o instanceof BillingInfo)) return false;
        BillingInfo that = (BillingInfo) o;
        return Objects.equals(customerId, that.customerId) &&
               Objects.equals(productCode, that.productCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(customerId, productCode);
    }

}
