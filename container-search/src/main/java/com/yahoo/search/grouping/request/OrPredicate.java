// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import com.yahoo.api.annotations.Beta;

import java.util.List;

/**
 * Represents a filter expression that or the subexpression.
 *
 * @author johsol
 */
@Beta
public class OrPredicate extends FilterExpression {

        private final List<FilterExpression> args;

        public OrPredicate(List<FilterExpression> args) {
            this.args = args;
        }

        public List<FilterExpression> getArgs() { return args; }

        @Override public String toString() { return "or(%s)".formatted(args); }
        @Override public FilterExpression copy() { return new OrPredicate(args.stream().map(FilterExpression::copy).toList()); }
    }