// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.ranking;

import ai.vespa.models.evaluation.FunctionEvaluator;
import com.yahoo.search.result.FeatureData;
import com.yahoo.search.result.Hit;
import com.yahoo.tensor.Tensor;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class SimpleEvaluator implements Evaluator {

    private final FunctionEvaluator evaluator;
    private final Set<String> neededInputs;
    
    public SimpleEvaluator(FunctionEvaluator prototype) {
        this.evaluator = prototype;
        this.neededInputs = new HashSet<String>(prototype.function().arguments());
    }

    @Override
    public Collection<String> needInputs() { return List.copyOf(neededInputs); }

    @Override
    public SimpleEvaluator bind(String name, Tensor value) {
        if (value != null) evaluator.bind(name, value);
        neededInputs.remove(name);
        return this;
    }

    @Override
    public double evaluateScore() {
        return evaluator.evaluate().asDouble();
    }

    @Override
    public String toString() {
        var buf = new StringBuilder();
        buf.append("SimpleEvaluator(");
        buf.append(evaluator.function().toString());
        buf.append(")[");
        for (String arg : neededInputs) {
            buf.append("{").append(arg).append("}");
        }
        buf.append("]");
        return buf.toString();
    }
}
