package com.yahoo.searchlib.rankingexpression.integration.tensorflow;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The result of importing a TensorFlow model into Vespa.
 * - A set of signatures which are named collections of inputs and outputs.
 * - A set of named constant tensors represented by Variable nodes in TensorFlow.
 * - A list of warning messages.
 *
 * @author bratseth
 */
// This object can be built incrementally within this package, but is immutable when observed from outside the package
public class ImportResult {

    private final Map<String, Signature> signatures = new HashMap<>();
    private final Map<String, TensorType> arguments = new HashMap<>();
    private final Map<String, Tensor> constants = new HashMap<>();
    private final Map<String, RankingExpression> expressions = new HashMap<>();
    private final List<String> warnings = new ArrayList<>();

    void argument(String name, TensorType argumentType) { arguments.put(name, argumentType); }
    void constant(String name, Tensor constant) { constants.put(name, constant); }
    void expression(String name, RankingExpression expression) { expressions.put(name, expression); }
    void warn(String warning) { warnings.add(warning); }

    /** Returns the given signature. If it does not already exist it is added to this. */
    Signature signature(String name) {
        return signatures.computeIfAbsent(name, n -> new Signature(n));
    }

    /** Returns an immutable map of the arguments ("Placeholders") of this */
    public Map<String, TensorType> arguments() { return Collections.unmodifiableMap(arguments); }

    /** Returns an immutable map of the constants of this */
    public Map<String, Tensor> constants() { return Collections.unmodifiableMap(constants); }

    /**
     * Returns an immutable map of the expressions of this - corresponding to TensorFlow nodes
     * which are not Placeholders or Variables (which instead become respectively arguments and constants).
     * Note that only nodes recursively referenced by a placeholder are added.
     */
    public Map<String, RankingExpression> expressions() { return Collections.unmodifiableMap(expressions); }

    /** Returns an immutable list, in natural sort order of the warnings generated while importing this */
    public List<String> warnings() {
        return warnings.stream().sorted().collect(Collectors.toList());
    }

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

        Signature(String name) {
            this.name = name;
        }

        void input(String inputName, String argumentName) { inputs.put(inputName, argumentName); }
        void output(String name, String expressionName) { outputs.put(name, expressionName); }

        /** Returns the result this is part of */
        ImportResult owner() { return ImportResult.this; }

        /**
         * Returns an immutable map of the inputs (evaluation context) of this. This is a map from input name
         * to argument (Placeholder) name in the owner of this
         */
        public Map<String, String> inputs() { return Collections.unmodifiableMap(inputs); }

        /** Returns owner().arguments().get(inputs.get(name)), e.g the type of the argument this input references */
        public TensorType inputArgument(String inputName) { return owner().arguments().get(inputs.get(inputName)); }

        /** Returns an immutable list of the expression names of this */
        public Map<String, String> outputs() { return Collections.unmodifiableMap(outputs); }

        /** Returns owner().expressions().get(outputs.get(outputName)), e.g the expression this output references */
        public RankingExpression outputExpression(String outputName) { return owner().expressions().get(outputs.get(outputName)); }

        @Override
        public String toString() { return "signature '" + name + "'"; }

    }

}
