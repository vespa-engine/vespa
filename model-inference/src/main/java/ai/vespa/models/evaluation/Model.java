// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.evaluation;

import com.google.common.collect.ImmutableList;
import com.yahoo.searchlib.rankingexpression.ExpressionFunction;

import java.util.Collection;
import java.util.List;

/**
 * A named collection of functions
 *
 * @author bratseth
 */
public class Model {

    private final String name;

    private final ImmutableList<ExpressionFunction> functions;

    public Model(String name, Collection<ExpressionFunction> functions) {
        this.name = name;
        this.functions = ImmutableList.copyOf(functions);
    }

    public String name() { return name; }

    /** Returns an immutable list of the expression functions of this */
    public List<ExpressionFunction> functions() { return functions; }

    @Override
    public String toString() { return "Model '" + name + "'"; }

}
