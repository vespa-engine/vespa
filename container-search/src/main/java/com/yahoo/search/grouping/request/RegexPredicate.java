// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import com.yahoo.api.annotations.Beta;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Represents a filter expression that matches a value from the evaluated expression against a regex.
 *
 * @author bjorncs
 */
@Beta
public class RegexPredicate extends FilterExpression {

    private final String pattern;
    private final GroupingExpression expression;

    public RegexPredicate(String pattern, GroupingExpression expression) {
        validateRegex(pattern);
        validateExpression(expression);
        this.pattern = pattern;
        this.expression = expression;
    }

    private static void validateRegex(String pattern) {
        try {
            // The assumption is that Java's Pattern is roughly similar to the re2 engine used on the search nodes.
            Pattern.compile(pattern);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("Invalid regex pattern: %s (%s)".formatted(pattern, e.getMessage()), e);
        }
    }

    private static void validateExpression(GroupingExpression exp) {
        // Fail in obviously invalid expressions
        if (exp instanceof BucketValue)
            throw new IllegalArgumentException("Regex predicate cannot be used with a bucket value");
        else if (exp instanceof AggregatorNode)
            throw new IllegalArgumentException("Regex predicate cannot be used with an aggregator");
        else if (exp instanceof PredefinedFunction)
            throw new IllegalArgumentException("Regex predicate cannot be used with a predefined function");
        else if (exp instanceof FixedWidthFunction)
            throw new IllegalArgumentException("Regex predicate cannot be used with a fixed width function");
    }

    public String getPattern() { return pattern; }
    public GroupingExpression getExpression() { return expression; }

    @Override public String toString() { return "regex(\"%s\", %s)".formatted(pattern, expression); }
    @Override public FilterExpression copy() { return new RegexPredicate(pattern, expression.copy()); }
}
