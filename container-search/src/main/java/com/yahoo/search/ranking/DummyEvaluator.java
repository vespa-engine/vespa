// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.ranking;

import ai.vespa.models.evaluation.FunctionEvaluator;
import com.yahoo.search.result.FeatureData;
import com.yahoo.search.result.Hit;
import com.yahoo.tensor.Tensor;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class DummyEvaluator implements Evaluator {

    private final String input;
    private Tensor result = null;

    DummyEvaluator(String input) {
        this.input = input;
    }

    @Override
    public Evaluator bind(String name, Tensor value) {
        result = value;
        return this;
    }

    @Override
    public double evaluateScore() {
        return result.asDouble();
    }

    @Override
    public String toString() {
        return "DummyEvaluator(" + input + ")";
    }
}
