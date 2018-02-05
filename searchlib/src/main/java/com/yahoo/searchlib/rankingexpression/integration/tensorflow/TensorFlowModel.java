// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.tensorflow;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The result of importing a TensorFlow model into Vespa.
 * - A set of signatures which are named collections of inputs and outputs.
 * - A set of named constant tensors represented by Variable nodes in TensorFlow.
 * - A list of warning messages.
 *
 * @author bratseth
 */
// This object can be built incrementally within this package, but is immutable when observed from outside the package
public class TensorFlowModel {

    private final Map<String, Signature> signatures = new HashMap<>();
    private final Map<String, TensorType> arguments = new HashMap<>();
    private final Map<String, Tensor> smallConstants = new HashMap<>();
    private final Map<String, Tensor> largeConstants = new HashMap<>();
    private final Map<String, RankingExpression> expressions = new HashMap<>();
    private final Map<String, RankingExpression> macros = new HashMap<>();
    private final Map<String, TensorType> requiredMacros = new HashMap<>();

    void argument(String name, TensorType argumentType) { arguments.put(name, argumentType); }
    void smallConstant(String name, Tensor constant) { smallConstants.put(name, constant); }
    void largeConstant(String name, Tensor constant) { largeConstants.put(name, constant); }
    void expression(String name, RankingExpression expression) { expressions.put(name, expression); }
    void macro(String name, RankingExpression expression) { macros.put(name, expression); }
    void requiredMacro(String name, TensorType type) { requiredMacros.put(name, type); }

    /** Returns the given signature. If it does not already exist it is added to this. */
    Signature signature(String name) {
        return signatures.computeIfAbsent(name, Signature::new);
    }

    /** Returns an immutable map of the arguments ("Placeholders") of this */
    public Map<String, TensorType> arguments() { return Collections.unmodifiableMap(arguments); }

    /**
     * Returns an immutable map of the small constants of this.
     * These should have sizes up to a few kb at most, and correspond to constant
     * values given in the TensorFlow source.
     */
    public Map<String, Tensor> smallConstants() { return Collections.unmodifiableMap(smallConstants); }

    /**
     * Returns an immutable map of the large constants of this.
     * These can have sizes in gigabytes and must be distributed to nodes separately from configuration,
     * and correspond to Variable files stored separately in TensorFlow.
     */
    public Map<String, Tensor> largeConstants() { return Collections.unmodifiableMap(largeConstants); }

    /**
     * Returns an immutable map of the expressions of this - corresponding to TensorFlow nodes
     * which are not Placeholders or Variables (which instead become respectively arguments and constants).
     * Note that only nodes recursively referenced by a placeholder are added.
     */
    public Map<String, RankingExpression> expressions() { return Collections.unmodifiableMap(expressions); }

    /** Returns an immutable map of macros that are part of this model */
    public Map<String, RankingExpression> macros() { return Collections.unmodifiableMap(macros); }

    /** Returns an immutable map of the macros that must be provided by the environment running this model */
    public Map<String, TensorType> requiredMacros() { return Collections.unmodifiableMap(requiredMacros); }

    /** Returns an immutable map of the signatures of this */
    public Map<String, Signature> signatures() { return Collections.unmodifiableMap(signatures); }

    /**
     * A signature is a set of named inputs and outputs, where the inputs maps to argument ("placeholder") names+types,
     * and outputs maps to expressions nodes.
     */
    public class Signature {

        private final String name;
        private final Map<String, String> inputs = new HashMap<>();
        private final Map<String, String> outputs = new HashMap<>();
        private final Map<String, String> skippedOutputs = new HashMap<>();
        private final List<String> importWarnings = new ArrayList<>();

        Signature(String name) {
            this.name = name;
        }

        void input(String inputName, String argumentName) { inputs.put(inputName, argumentName); }
        void output(String name, String expressionName) { outputs.put(name, expressionName); }
        void skippedOutput(String name, String reason) { skippedOutputs.put(name, reason); }
        void importWarning(String warning) { importWarnings.add(warning); }

        public String name() { return name; }

        /** Returns the result this is part of */
        TensorFlowModel owner() { return TensorFlowModel.this; }

        /**
         * Returns an immutable map of the inputs (evaluation context) of this. This is a map from input name
         * to argument (Placeholder) name in the owner of this
         */
        public Map<String, String> inputs() { return Collections.unmodifiableMap(inputs); }

        /** Returns owner().arguments().get(inputs.get(name)), e.g the type of the argument this input references */
        public TensorType inputArgument(String inputName) { return owner().arguments().get(inputs.get(inputName)); }

        /** Returns an immutable list of the expression names of this */
        public Map<String, String> outputs() { return Collections.unmodifiableMap(outputs); }

        /**
         * Returns an immutable list of the outputs of this which could not be imported,
         * with a string detailing the reason for each
         */
        public Map<String, String> skippedOutputs() { return Collections.unmodifiableMap(skippedOutputs); }

        /**
         * Returns an immutable list of possibly non-fatal warnings encountered during import.
         */
        public List<String> importWarnings() { return Collections.unmodifiableList(importWarnings); }

        /** Returns owner().expressions().get(outputs.get(outputName)), e.g the expression this output references */
        public RankingExpression outputExpression(String outputName) { return owner().expressions().get(outputs.get(outputName)); }

        @Override
        public String toString() { return "signature '" + name + "'"; }

    }

}
