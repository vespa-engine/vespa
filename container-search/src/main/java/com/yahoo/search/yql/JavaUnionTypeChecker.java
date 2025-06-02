// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

import java.util.Set;

public class JavaUnionTypeChecker extends OperatorTypeChecker {

    private final Set<Class<?>> types;

    public JavaUnionTypeChecker(Operator parent, int idx, Set<Class<?>> types) {
        super(parent, idx);
        this.types = types;
    }

    public JavaUnionTypeChecker(Operator parent, int idx, Class<?>... types) {
        super(parent, idx);
        this.types = ImmutableSet.copyOf(types);
    }

    @Override
    public void check(Object argument) {
        Preconditions.checkNotNull(argument, "Argument %s of %s must not be null", idx, parent);
        for (Class<?> candidate : types) {
            if (candidate.isInstance(argument)) {
                return;
            }
        }
        Preconditions.checkArgument(false, "Argument %s of %s must be %s (is: %s).", idx, parent, Joiner.on("|").join(types), argument.getClass());
    }

}
