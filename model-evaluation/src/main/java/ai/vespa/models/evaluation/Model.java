// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.evaluation;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.yahoo.searchlib.rankingexpression.ExpressionFunction;
import com.yahoo.searchlib.rankingexpression.evaluation.ContextIndex;
import com.yahoo.searchlib.rankingexpression.evaluation.ExpressionOptimizer;

import java.util.Arrays;
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
@Beta
public class Model {

    private final String name;

    /** Free functions */
    private final ImmutableList<ExpressionFunction> functions;

    /** Instances of each usage of the above function, where variables (if any) are replaced by their bindings */
    private final ImmutableMap<FunctionReference, ExpressionFunction> referencedFunctions;

    /** Context prototypes, indexed by function name (as all invocations of the same function share the same context prototype) */
    private final ImmutableMap<String, LazyArrayContext> contextPrototypes;

    private final ExpressionOptimizer expressionOptimizer = new ExpressionOptimizer();

    /** Programmatically create a model containing functions without constant of function references only */
    public Model(String name, Collection<ExpressionFunction> functions) {
        this(name, functions, Collections.emptyMap(), Collections.emptyList());
    }

    Model(String name,
          Collection<ExpressionFunction> functions,
          Map<FunctionReference, ExpressionFunction> referencedFunctions,
          List<Constant> constants) {
        // TODO: Optimize functions
        this.name = name;
        this.functions = ImmutableList.copyOf(functions);

        ImmutableMap.Builder<String, LazyArrayContext> contextBuilder = new ImmutableMap.Builder<>();
        for (ExpressionFunction function : functions) {
            try {
                contextBuilder.put(function.getName(),
                                   new LazyArrayContext(function.getBody(), referencedFunctions, constants, this));
            }
            catch (RuntimeException e) {
                throw new IllegalArgumentException("Could not prepare an evaluation context for " + function, e);
            }
        }
        this.contextPrototypes = contextBuilder.build();

        ImmutableMap.Builder<FunctionReference, ExpressionFunction> functionsBuilder = new ImmutableMap.Builder<>();
        for (Map.Entry<FunctionReference, ExpressionFunction> function : referencedFunctions.entrySet()) {
            ExpressionFunction optimizedFunction = optimize(function.getValue(),
                                                            contextPrototypes.get(function.getKey().functionName()));
            functionsBuilder.put(function.getKey(), optimizedFunction);
        }
        this.referencedFunctions = functionsBuilder.build();
    }

    /** Returns an optimized version of the given function */
    private ExpressionFunction optimize(ExpressionFunction function, ContextIndex context) {
        // Note: Optimization is in-place but we do not depend on that outside this method
        expressionOptimizer.optimize(function.getBody(), context);
        return function;
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

    /** Returns an immutable map of the referenced function instances of this */
    Map<FunctionReference, ExpressionFunction> referencedFunctions() { return referencedFunctions; }

    /** Returns the given referred function, or throws a IllegalArgumentException if it does not exist */
    ExpressionFunction requireReferencedFunction(FunctionReference reference) {
        ExpressionFunction function = referencedFunctions.get(reference);
        if (function == null)
            throw new IllegalArgumentException("No " + reference + " in " + this + ". References: " +
                                               referencedFunctions.keySet().stream()
                                                                           .map(FunctionReference::serialForm)
                                                                           .collect(Collectors.joining(", ")));
        return function;
    }

    /**
     * Returns an evaluator which can be used to evaluate the given function in a single thread once.
     *
     * Usage:
     * <code>Tensor result = model.evaluatorOf("myFunction").bind("foo", value).bind("bar", value).evaluate()</code>
     *
     * @param names the names identifying the function - this can be from 0 to 2, specifying function or "signature"
     *              name, and "output", respectively. Names which are unnecessary to determine the desired function
     *              uniquely (e.g if there is just one function or output) can be omitted.
     *              A two-component name can alternatively be specified as a single argument with components separated
     *              by dot.
     * @throws IllegalArgumentException if the function is not present, or not uniquely identified by the names given
     */
    public FunctionEvaluator evaluatorOf(String ... names) {  // TODO: Parameter overloading?
        if (names.length == 0) {
            if (functions.size() > 1)
                throwUndeterminedFunction("More than one function is available in " + this + ", but no name is given");
            return evaluatorOf(functions.get(0));
        }
        else if (names.length == 1) {
            String name = names[0];
            ExpressionFunction function = function(name);
            if (function != null) return evaluatorOf(function);

            List<ExpressionFunction> functionsStartingByName =
                    functions.stream().filter(f -> f.getName().startsWith(name + ".")).collect(Collectors.toList());
            if (functionsStartingByName.size() == 0)
                throwUndeterminedFunction("No function '" + name + "' in " + this);
            else if (functionsStartingByName.size() == 1)
                return evaluatorOf(functionsStartingByName.get(0));
            else
                throwUndeterminedFunction("Multiple functions start by '" + name + "' in " + this);

        }
        else if (names.length == 2) {
            String name = names[0] + "." + names[1];
            ExpressionFunction function = function(name);
            if (function == null) throwUndeterminedFunction("No function '" + name + "' in " + this);
            return evaluatorOf(function);
        }
        throw new IllegalArgumentException("No more than 2 names can be given when choosing a function, got " +
                                           Arrays.toString(names));
    }

    /** Returns a single-use evaluator of a function */
    private FunctionEvaluator evaluatorOf(ExpressionFunction function) {
        return new FunctionEvaluator(function, requireContextProprotype(function.getName()).copy());
    }

    private void throwUndeterminedFunction(String message) {
        throw new IllegalArgumentException(message + ". Available functions: " +
                                           functions.stream().map(f -> f.getName()).collect(Collectors.joining(", ")));
    }

    @Override
    public String toString() { return "model '" + name + "'"; }

}
