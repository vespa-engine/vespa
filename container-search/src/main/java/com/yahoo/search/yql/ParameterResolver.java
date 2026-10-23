// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

import com.yahoo.processing.request.Properties;

import java.util.Map;
import java.util.Objects;

/**
 * Resolves parameter references in a query language, such as <code>@name</code> in YQL, to their values.
 * Values are substituted verbatim where the reference appears and are never parsed as query syntax,
 * which is what makes parameter substitution safe against query injection.
 *
 * @author johsol
 */
@FunctionalInterface
interface ParameterResolver {

    /** Returns the value of the named parameter, or null if it is not set. */
    String get(String name);

    /** Returns a resolver which looks up parameters in the given query properties. */
    static ParameterResolver of(Properties properties) {
        Objects.requireNonNull(properties, "properties cannot be null");
        return properties::getString;
    }

    /** Returns a resolver which looks up parameters in the given map. */
    static ParameterResolver of(Map<String, String> values) {
        Objects.requireNonNull(values, "values cannot be null");
        return values::get;
    }

    /** Returns a resolver which has no parameters set. */
    static ParameterResolver empty() {
        return name -> null;
    }

}
