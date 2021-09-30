// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.billing;

import java.util.Objects;
import java.util.Optional;

/**
 * Object to carry the result of {@link BillingController#setCollectionMethod}
 *
 * @author smorgrav
 */
public class CollectionResult {

    private final Optional<String> errorMessage;

    private CollectionResult(Optional<String> errorMessage) {
        this.errorMessage = errorMessage;
    }

    public static CollectionResult success() {
        return new CollectionResult(Optional.empty());
    }

    public static CollectionResult error(String errorMessage) {
        return new CollectionResult(Optional.of(errorMessage));
    }

    public boolean isSuccess() {
        return errorMessage.isEmpty();
    }

    public Optional<String> getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        return "CollectionResult{" +
                "errorMessage=" + errorMessage +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CollectionResult that = (CollectionResult) o;
        return Objects.equals(errorMessage, that.errorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(errorMessage);
    }
}