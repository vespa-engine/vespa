// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.rule;

import com.yahoo.searchlib.rankingexpression.ExpressionFunction;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.TypeContext;
import static com.yahoo.searchlib.rankingexpression.Reference.wrapInRankingExpression;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Context needed to serialize an expression to a string. This has the lifetime of a single serialization
 *
 * @author bratseth
 */
public class SerializationContext extends FunctionReferenceContext {
    
    /** Serialized form of functions indexed by name */
    private final Map<String, String> serializedFunctions;

    private final Optional<TypeContext<Reference>> typeContext;

    /** Create a context for a single serialization task */
    public SerializationContext() {
        this(Collections.emptyList(), Collections.emptyMap(), Optional.empty(), new LinkedHashMap<>());
    }

    /**
     * Create a context for a single serialization task
     *
     * @param functions the functions of this
     * @param typeContext the type context of this: Serialization may depend on type resolution
     */
    public SerializationContext(Collection<ExpressionFunction> functions,
                                Optional<TypeContext<Reference>> typeContext) {
        this(functions, Collections.emptyMap(), typeContext, new LinkedHashMap<>());
    }

    /**
     * Create a context for a single serialization task
     *
     * @param functions the functions of this
     * @param bindings the arguments of this
     * @param typeContext the type context of this: Serialization may depend on type resolution
     */
    public SerializationContext(Collection<ExpressionFunction> functions,
                                Map<String, String> bindings,
                                TypeContext<Reference> typeContext) {
        this(functions, bindings, Optional.of(typeContext), new LinkedHashMap<>());
    }

    /**
     * Create a context for a single serialization task
     *
     * @param functions the functions of this
     * @param bindings the arguments of this
     * @param typeContext the type context of this: Serialization may depend on type resolution
     * @param serializedFunctions a cache of serializedFunctions - the ownership of this map
     *        is <b>transferred</b> to this and will be modified in it
     */
    private SerializationContext(Collection<ExpressionFunction> functions,
                                 Map<String, String> bindings,
                                 Optional<TypeContext<Reference>> typeContext,
                                 Map<String, String> serializedFunctions) {
        this(toMap(functions), bindings, typeContext, serializedFunctions);
    }

    public SerializationContext(Map<String, ExpressionFunction> functions,
                                Map<String, String> bindings,
                                Optional<TypeContext<Reference>> typeContext,
                                Map<String, String> serializedFunctions) {
        super(functions, bindings);
        this.typeContext = typeContext;
        this.serializedFunctions = serializedFunctions;
    }

    /** Returns the type context of this, if it is able to resolve types. */
    public Optional<TypeContext<Reference>> typeContext() { return typeContext; }

    private static Map<String, ExpressionFunction> toMap(Collection<ExpressionFunction> list) {
        Map<String,ExpressionFunction> mapBuilder = new HashMap<>();
        for (ExpressionFunction function : list)
            mapBuilder.put(function.getName(), function);
        return Map.copyOf(mapBuilder);
    }

    /** Adds the serialization of a function */
    public void addFunctionSerialization(String name, String expressionString) {
        serializedFunctions.put(name, expressionString);
    }

    /** Adds the serialization of the argument type to a function */
    public void addArgumentTypeSerialization(String functionName, String argumentName, TensorType type) {
        serializedFunctions.put(wrapInRankingExpression(functionName) + "." + argumentName + ".type", type.toString());
    }

    /** Adds the serialization of the return type of a function */
    public void addFunctionTypeSerialization(String functionName, TensorType type) {
        if (type.rank() == 0) return; // no explicit type implies scalar (aka rank 0 tensor)
        String key = wrapInRankingExpression(functionName) + ".type";
        var old = serializedFunctions.put(key, type.toString());
        if (old != null && !old.equals(type.toString())) {
            throw new IllegalArgumentException("conflicting values for " + key + ": " + old + " != " + type.toString());
        }
    }

    @Override
    public SerializationContext withBindings(Map<String, String> bindings) {
        return new SerializationContext(getFunctions(), bindings, typeContext, this.serializedFunctions);
    }

    /** Returns a fresh context without bindings */
    @Override
    public SerializationContext withoutBindings() {
        return new SerializationContext(getFunctions(), null, typeContext, this.serializedFunctions);
    }

    public Map<String, String> serializedFunctions() { return serializedFunctions; }

    public boolean needSerialization(String functionName) {
        return ! serializedFunctions().containsKey(RankingExpression.propertyName(functionName));
    }

}
