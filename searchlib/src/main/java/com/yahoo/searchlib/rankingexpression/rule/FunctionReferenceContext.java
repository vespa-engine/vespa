// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.rule;

import com.google.common.collect.ImmutableMap;
import com.yahoo.searchlib.rankingexpression.ExpressionFunction;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The context of a function invocation.
 *
 * @author bratseth
 */
public class FunctionReferenceContext {

    /** Expression functions indexed by name */
    private final ImmutableMap<String, ExpressionFunction> functions;

    /** Mapping from argument names to the expressions they resolve to */
    // TODO: Make private
    public final Map<String, String> bindings = new HashMap<>();

    /** Create a context for a single serialization task */
    public FunctionReferenceContext() {
        this(Collections.emptyList());
    }

    /** Create a context for a single serialization task */
    public FunctionReferenceContext(Collection<ExpressionFunction> functions) {
        this(toMap(functions), Collections.emptyMap());
    }

    public FunctionReferenceContext(Collection<ExpressionFunction> functions, Map<String, String> bindings) {
        this(toMap(functions), bindings);
    }

    /** Create a context for a single serialization task */
    public FunctionReferenceContext(Map<String, ExpressionFunction> functions) {
        this(functions.values());
    }

    /** Create a context for a single serialization task */
    public FunctionReferenceContext(Map<String, ExpressionFunction> functions, Map<String, String> bindings) {
        this.functions = ImmutableMap.copyOf(functions);
        if (bindings != null)
            this.bindings.putAll(bindings);
    }

    private static ImmutableMap<String, ExpressionFunction> toMap(Collection<ExpressionFunction> list) {
        ImmutableMap.Builder<String,ExpressionFunction> mapBuilder = new ImmutableMap.Builder<>();
        for (ExpressionFunction function : list)
            mapBuilder.put(function.getName(), function);
        return mapBuilder.build();
    }

    /**
     * Returns a function or null if it isn't defined in this context
     */
    public ExpressionFunction getFunction(String name) { return functions.get(name); }

    protected Map<String, ExpressionFunction> functions() { return functions; }

    /** Returns the resolution of an argument, or null if it isn't defined in this context */
    public String getBinding(String name) { return bindings.get(name); }

    /** Returns a new context with the bindings replaced by the given bindings */
    public FunctionReferenceContext withBindings(Map<String, String> bindings) {
        return new FunctionReferenceContext(this.functions, bindings);
    }

}
