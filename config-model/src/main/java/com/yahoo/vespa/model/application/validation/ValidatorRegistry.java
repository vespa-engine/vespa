// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.vespa.model.application.validation.change.ChangeValidator;

import java.util.Collection;
import java.util.List;

/**
 * A provider of additional validators, injected via DI (e.g., for cloud applications).
 *
 * @author bjorncs
 */
public interface ValidatorRegistry {

    /** Returns validators to run on every deployment. */
    default Collection<Validator> validators() { return List.of(); }

    /** Returns change validators to run when there is a previous model. */
    default Collection<ChangeValidator> changeValidators() { return List.of(); }

}
