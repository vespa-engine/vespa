// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import com.yahoo.config.application.api.ValidationId;

/**
 * Sub-interface for {@link ConfigChangeAction} children that may be disallowed.
 *
 * @author jonmv
 */
public interface DisallowableConfigChangeAction extends ConfigChangeAction {

    /** Returns the validation ID used to allow deployment when this action is required. */
    ValidationId validationId();

}
