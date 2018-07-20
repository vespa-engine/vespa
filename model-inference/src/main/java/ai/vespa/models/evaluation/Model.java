// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.evaluation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.yahoo.searchlib.rankingexpression.ExpressionFunction;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A named collection of functions
 *
 * @author bratseth
 */
public class Model {

    private final String name;

    /** Free functions */
    private final ImmutableList<ExpressionFunction> functions;

    /** Instances of each usage of the above function, where variables (if any) are replaced by their bindings */
    private final ImmutableMap<String, ExpressionFunction> referredFunctions;

    private final ImmutableMap<String, LazyArrayContext> contextPrototypes;

    public Model(String name, Collection<ExpressionFunction> functions) {
        this(name, functions, Collections.emptyList());
    }

    Model(String name, Collection<ExpressionFunction> functions, Collection<ExpressionFunction> referredFunctions) {
        // TODO: Optimize functions
        this.name = name;
        this.functions = ImmutableList.copyOf(functions);

        ImmutableMap.Builder<String, ExpressionFunction> functionsBuilder = new ImmutableMap.Builder<>();
        for (ExpressionFunction function : referredFunctions)
            functionsBuilder.put(function.getName(), function);
        this.referredFunctions = functionsBuilder.build();

        ImmutableMap.Builder<String, LazyArrayContext> contextBuilder = new ImmutableMap.Builder<>();
        for (ExpressionFunction function : functions) {
            try {
                contextBuilder.put(function.getName(), new LazyArrayContext(function.getBody(), this.referredFunctions));
            }
            catch (RuntimeException e) {
                throw new IllegalArgumentException("Could not prepare an evaluation context for " + function, e);
            }
        }
        this.contextPrototypes = contextBuilder.build();
    }

    public String name() { return name; }

    /** Returns an immutable list of the free functions of this */
    public List<ExpressionFunction> functions() { return functions; }

    /** Returns the given function, or throws a IllegalArgumentException if it does not exist */
    ExpressionFunction requireFunction(String name) {
        ExpressionFunction function = function(name);
        if (function == null)
            throw new IllegalArgumentException("No function named '" + name + "' in " + this + ". Available functions: " +
                                               functions.stream().map(f -> f.getName()).collect(Collectors.joining(", ")));
        return function;
    }

    /** Returns the given function, or throws a IllegalArgumentException if it does not exist */
    private LazyArrayContext requireContextProprotype(String name) {
        LazyArrayContext context = contextPrototypes.get(name);
        if (context == null) // Implies function is not present
            throw new IllegalArgumentException("No function named '" + name + "' in " + this + ". Available functions: " +
                                               functions.stream().map(f -> f.getName()).collect(Collectors.joining(", ")));
        return context;
    }

    /** Returns the function withe the given name, or null if none */ // TODO: Parameter overloading?
    ExpressionFunction function(String name) {
        for (ExpressionFunction function : functions)
            if (function.getName().equals(name))
                return function;
        return null;
    }

    /** Returns an immutable map of the bound function instances of this, indexed by the bound instance if */
    Map<String, ExpressionFunction> boundFunctions() { return referredFunctions; }

    /**
     * Returns an evaluator which can be used to evaluate the given function in a single thread once.

     * Usage:
     * <code>Tensor result = model.evaluatorOf("myFunction").bind("foo", value).bind("bar", value).evaluate()</code>
     *
     * @throws IllegalArgumentException if the function is not present
     */
    public FunctionEvaluator evaluatorOf(String function) {  // TODO: Parameter overloading?
        return new FunctionEvaluator(requireContextProprotype(function).copy());
    }

    @Override
    public String toString() { return "model '" + name + "'"; }

}
