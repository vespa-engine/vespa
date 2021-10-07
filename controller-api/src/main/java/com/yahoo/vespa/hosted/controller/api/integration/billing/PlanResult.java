// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.billing;

import java.util.Optional;

/**
 * Result of {@link BillingController#setPlan}
 *
 * @author olaa
 */
public class PlanResult {

    private final Optional<String> errorMessage;

    private PlanResult(Optional<String> errorMessage) {
        this.errorMessage = errorMessage;
    }

    public static PlanResult success() {
        return new PlanResult(Optional.empty());
    }

    public static PlanResult error(String errorMessage) {
        return new PlanResult(Optional.of(errorMessage));
    }

    public boolean isSuccess() {
        return errorMessage.isEmpty();
    }

    public Optional<String> getErrorMessage() {
        return errorMessage;
    }

}
