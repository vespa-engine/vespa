// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

/**
 * Abstract superclass of all application package validators.
 *
 * @author hmusum
 */
public interface Validator {

    /**
     * Validates the input Vespa model; illegal configuration should be reported through the context,
     * while other problems (system error, insufficient quota, etc.) should be thrown.
     */
    void validate(Validation.Context context);

}
