// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived.validation;

import com.yahoo.schema.Schema;
import com.yahoo.schema.derived.DerivedConfiguration;

public class Validation {

    public static void validate(DerivedConfiguration config, Schema schema) {
        new IndexStructureValidator(config, schema).validate();
    }
}
