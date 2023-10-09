// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

/**
 * @author hmusum
 */
public class ValidationParameters {

    public enum IgnoreValidationErrors {TRUE, FALSE}

    public enum FailOnIncompatibleChange {TRUE, FALSE} //Note: Default is FALSE

    public enum CheckRouting {TRUE, FALSE}

    private final IgnoreValidationErrors ignoreValidationErrors;
    private final FailOnIncompatibleChange failOnIncompatibleChange;
    private final CheckRouting checkRouting;

    public ValidationParameters() {
        this(IgnoreValidationErrors.FALSE);
    }

    public ValidationParameters(IgnoreValidationErrors ignoreValidationErrors) {
        this(ignoreValidationErrors, FailOnIncompatibleChange.FALSE, CheckRouting.TRUE);
    }

    public ValidationParameters(CheckRouting checkRouting) {
        this(IgnoreValidationErrors.FALSE, FailOnIncompatibleChange.FALSE, checkRouting);
    }

    public ValidationParameters(IgnoreValidationErrors ignoreValidationErrors,
                                FailOnIncompatibleChange failOnIncompatibleChange,
                                CheckRouting checkRouting) {
        this.ignoreValidationErrors = ignoreValidationErrors;
        this.failOnIncompatibleChange = failOnIncompatibleChange;
        this.checkRouting = checkRouting;
    }

    public boolean ignoreValidationErrors() {
        return ignoreValidationErrors == IgnoreValidationErrors.TRUE;
    }

    public boolean failOnIncompatibleChanges() {
        return failOnIncompatibleChange == FailOnIncompatibleChange.TRUE;
    }

    public boolean checkRouting() {
        return checkRouting == CheckRouting.TRUE;
    }
}
