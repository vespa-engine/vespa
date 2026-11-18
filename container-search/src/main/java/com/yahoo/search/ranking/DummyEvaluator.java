// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.ranking;

import com.yahoo.tensor.Tensor;

import java.time.Instant;

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

    // This evaluator returns a bound value and evaluates no model, so it has nothing to time out.
    @Override
    public Evaluator setDeadline(Instant deadline) { return this; }

    @Override
    public double evaluateScore() {
        return result.asDouble();
    }

    @Override
    public String toString() {
        return "DummyEvaluator(" + input + ")";
    }
}
