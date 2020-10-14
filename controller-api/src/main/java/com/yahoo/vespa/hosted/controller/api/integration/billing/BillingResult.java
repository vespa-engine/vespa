// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.billing;

import java.util.Optional;

/**
 * General result class for mutating operations on the {@link BillingController}
 *
 * @author olaa
 */
public class BillingResult {

    private final Optional<String> errorMessage;

    private BillingResult(Optional<String> errorMessage) {
        this.errorMessage = errorMessage;
    }

    public static BillingResult success() {
        return new BillingResult(Optional.empty());
    }

    public static BillingResult error(String errorMessage) {
        return new BillingResult(Optional.of(errorMessage));
    }

    public boolean isSuccess() {
        return errorMessage.isEmpty();
    }

    public Optional<String> getErrorMessage() {
        return errorMessage;
    }

}
