// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.rule;

import com.google.common.collect.ImmutableMap;
import com.yahoo.searchlib.rankingexpression.ExpressionFunction;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Context needed to serialize an expression to a string. This has the lifetime of a single serialization
 *
 * @author bratseth
 */
public class SerializationContext {
    
    /** Expression functions indexed by name */
    private final ImmutableMap<String, ExpressionFunction> functions;

    /** A cache of already serialized expressions indexed by name */
    private final Map<String, String> serializedFunctions;

    /** Mapping from argument names to the expressions they resolve to */
    public final Map<String, String> bindings = new HashMap<>();

    /** Create a context for a single serialization task */
    public SerializationContext() {
        this(Collections.emptyList());
    }

    /** Create a context for a single serialization task */
    public SerializationContext(Collection<ExpressionFunction> functions) {
        this(functions, Collections.emptyMap(), new LinkedHashMap<>());
    }

    /** Create a context for a single serialization task */
    public SerializationContext(Map<String, ExpressionFunction> functions) {
        this(functions.values());
    }

    /** Create a context for a single serialization task */
    public SerializationContext(List<ExpressionFunction> functions, Map<String, String> bindings) {
        this(functions, bindings, new LinkedHashMap<>());
    }

    /**
     * Create a context for a single serialization task
     *
     * @param functions the functions of this
     * @param bindings the arguments of this
     * @param serializedFunctions a cache of serializedFunctions - the ownership of this map
     *        is <b>transferred</b> to this and will be modified in it
     */
    public SerializationContext(Collection<ExpressionFunction> functions, Map<String, String> bindings,
                                Map<String, String> serializedFunctions) {
        this(toMap(functions), bindings, serializedFunctions);
    }

    private static ImmutableMap<String, ExpressionFunction> toMap(Collection<ExpressionFunction> list) {
        ImmutableMap.Builder<String,ExpressionFunction> mapBuilder = new ImmutableMap.Builder<>();
        for (ExpressionFunction function : list)
            mapBuilder.put(function.getName(), function);
        return mapBuilder.build();
    }

    /**
     * Create a context for a single serialization task
     *
     * @param functions the functions of this
     * @param bindings the arguments of this
     * @param serializedFunctions a cache of serializedFunctions - the ownership of this map
     *        is <b>transferred</b> to this and will be modified in it
     */
    public SerializationContext(ImmutableMap<String,ExpressionFunction> functions, Map<String, String> bindings,
                                Map<String, String> serializedFunctions) {
        this.functions = functions;
        this.serializedFunctions = serializedFunctions;
        if (bindings != null)
            this.bindings.putAll(bindings);
    }

    /**
     * Returns a function or null if it isn't defined in this context
     */
    public ExpressionFunction getFunction(String name) { return functions.get(name); }

    /** Adds the serialization of a function */
    public void addFunctionSerialization(String name, String expressionString) {
        serializedFunctions.put(name, expressionString);
    }

    /** Returns the existing serialization of a function, or null if none */
    public String getFunctionSerialization(String name) {
        return serializedFunctions.get(name);
    }

    /**
     * Returns the resolution of an argument, or null if it isn't defined in this context
     */
    public String getBinding(String name) { return bindings.get(name); }

    /**
     * Returns a new context which shares the functions and serialized function map with this but has different
     * arguments.
     */
    public SerializationContext createBinding(Map<String, String> arguments) {
        return new SerializationContext(this.functions, arguments, this.serializedFunctions);
    }

    public Map<String, String> serializedFunctions() { return serializedFunctions; }

}
