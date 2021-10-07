// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived.validation;

import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.derived.DerivedConfiguration;

/**
 * @author mathiasm
 */
public abstract class Validator {

    protected DerivedConfiguration config;
    protected Search search;

    protected Validator(DerivedConfiguration config, Search search) {
        this.config = config;
        this.search = search;
    }

    public abstract void validate();

}
