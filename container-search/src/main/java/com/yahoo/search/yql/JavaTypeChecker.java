// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

import com.google.common.base.Preconditions;

class JavaTypeChecker extends OperatorTypeChecker {

    private final Class<?> type;

    public JavaTypeChecker(Operator parent, int idx, Class<?> type) {
        super(parent, idx);
        this.type = type;
    }

    @Override
    public void check(Object argument) {
        Preconditions.checkNotNull(argument, "Argument %s of %s must not be null", idx, parent);
        Preconditions.checkArgument(type.isInstance(argument), "Argument %s of %s must be %s (is: %s).", idx, parent, type.getName(), argument.getClass().getName());
    }

}
