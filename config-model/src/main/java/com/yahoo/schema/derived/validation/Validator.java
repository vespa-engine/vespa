// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived.validation;

import com.yahoo.schema.Schema;
import com.yahoo.schema.derived.DerivedConfiguration;

/**
 * @author mathiasm
 */
public abstract class Validator {

    protected DerivedConfiguration config;
    protected Schema schema;

    protected Validator(DerivedConfiguration config, Schema schema) {
        this.config = config;
        this.schema = schema;
    }

    public abstract void validate();

}
