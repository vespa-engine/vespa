// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.google.common.collect.ImmutableList;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * A set of allows which suppresses specific validations in limited time periods.
 * This is useful to be able to complete a deployment in cases where the application
 * owner believes that the changes to be deployed have acceptable consequences.
 * Immutable.
 *
 * @author bratseth
 */
public class ValidationOverrides {

    private final List<Allow> overrides;

    /** Instant to use as "now". This is a field to allow unit testing. */
    private final Instant now;

    /** Creates validation overrides for the current instant */
    public ValidationOverrides(List<Allow> overrides) {
        this(overrides, Instant.now());
    }

    public ValidationOverrides(List<Allow> overrides, Instant now) {
        this.overrides = ImmutableList.copyOf(overrides);
        this.now = now;
        for (Allow override : overrides)
            if (now.plus(Duration.ofDays(30)).isBefore(override.until))
                throw new IllegalArgumentException(override + " is too far in the future: Max 30 days is allowed");
    }

    /** Throws a ValidationException unless this validation is overridden at this time */
    public void invalid(ValidationId validationId, String message) {
        if ( ! allows(validationId))
            throw new ValidationException(validationId, message);
    }

    public boolean allows(String validationIdString) {
        Optional<ValidationId> validationId = ValidationId.from(validationIdString);
        if ( ! validationId.isPresent()) return false; // unknown id -> not allowed
        return allows(validationId.get());
    }

    /** Returns whether the given (assumed invalid) change is allowed by this at the moment */
    public boolean allows(ValidationId validationId) {
        for (Allow override : overrides)
            if (override.allows(validationId, now))
                return true;
        return false;
    }

    public static ValidationOverrides empty() { return new ValidationOverrides(ImmutableList.of()); }

    /** A validation override which allows a particular change. Immutable. */
    public static class Allow {

        private final ValidationId validationId;
        private final Instant until;

        public Allow(ValidationId validationId, Instant until) {
            this.validationId = validationId;
            this.until = until;
        }

        public boolean allows(ValidationId validationId, Instant now) {
            return this.validationId.equals(validationId) && now.isBefore(until);
        }

        @Override
        public String toString() { return "allow '" + validationId + "' until " + until; }

    }

    /**
     * A deployment validation exception.
     * Deployment validations can be {@link ValidationOverrides overridden} based on their id.
     * The purpose of this exception is to model that id as a separate field.
     */
    public static class ValidationException extends IllegalArgumentException {

        private final ValidationId validationId;

        private ValidationException(ValidationId validationId, String message) {
            super(message);
            this.validationId = validationId;
        }

        /** Returns the unique id of this validation, which can be used to {@link ValidationOverrides override} it */
        public ValidationId validationId() { return validationId; }

        /** Returns "validationId: message" */
        @Override
        public String getMessage() { return validationId + ": " + super.getMessage(); }

    }

}
