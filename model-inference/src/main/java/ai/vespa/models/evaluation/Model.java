// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.evaluation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.yahoo.searchlib.rankingexpression.ExpressionFunction;
import com.yahoo.searchlib.rankingexpression.evaluation.ArrayContext;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;

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

    /** An instance of each usage of the above function, where variables are replaced by their bindings */
    private final ImmutableMap<String, ExpressionFunction> boundFunctions;

    public Model(String name, Collection<ExpressionFunction> functions) {
        this(name, functions, Collections.emptyList());
    }

    Model(String name, Collection<ExpressionFunction> functions, Collection<ExpressionFunction> boundFunctions) {
        this.name = name;
        this.functions = ImmutableList.copyOf(functions);
        ImmutableMap.Builder<String, ExpressionFunction> b = new ImmutableMap.Builder<>();
        for (ExpressionFunction function : boundFunctions)
            b.put(function.getName(), function);
        this.boundFunctions = b.build();
    }

    public String name() { return name; }

    /** Returns an immutable list of the free (callable) functions of this */
    public List<ExpressionFunction> functions() { return functions; }

    /** Returns the given function, or throws a IllegalArgumentException if it does not exist */
    ExpressionFunction requireFunction(String name) {
        ExpressionFunction function = function(name);
        if (function == null)
            throw new IllegalArgumentException("No function named '" + name + "' in " + this + ". Available functions: " +
                                               functions.stream().map(f -> f.getName()).collect(Collectors.joining(", ")));
        return function;
    }


    /** Returns the function withe the given name, or null if none */ // TODO: Parameter overloading?
    ExpressionFunction function(String name) {
        for (ExpressionFunction function : functions)
            if (function.getName().equals(name))
                return function;
        return null;
    }

    /** Returns an immutable map of the bound function instances of this, indexed by the bound instance if */
    Map<String, ExpressionFunction> boundFunctions() { return boundFunctions; }

    /**
     * Returns a function which can be used to evaluate the given function
     *
     * @throws IllegalArgumentException if the function is not present
     */
    // TODO: Rename to singleThreadedContextFor, move context protottype creation to construction, clone here
    public Context contextFor(String function) {
        return new LazyArrayContext(requireFunction(function).getBody(), boundFunctions);
    }

    @Override
    public String toString() { return "model '" + name + "'"; }

}
