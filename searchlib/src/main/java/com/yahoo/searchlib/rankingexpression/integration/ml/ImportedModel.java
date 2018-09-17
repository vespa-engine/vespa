package com.yahoo.searchlib.rankingexpression.integration.ml;

import com.yahoo.collections.Pair;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The result of importing an ML model into Vespa.
 *
 * @author bratseth
 */
public class ImportedModel {

    private static final String defaultSignatureName = "default";

    private static final Pattern nameRegexp = Pattern.compile("[A-Za-z0-9_]*");
    private final String name;
    private final String source;

    private final Map<String, Signature> signatures = new HashMap<>();
    private final Map<String, TensorType> arguments = new HashMap<>();
    private final Map<String, Tensor> smallConstants = new HashMap<>();
    private final Map<String, Tensor> largeConstants = new HashMap<>();
    private final Map<String, RankingExpression> expressions = new HashMap<>();
    private final Map<String, RankingExpression> functions = new HashMap<>();
    private final Map<String, TensorType> requiredFunctions = new HashMap<>();

    /**
     * Creates a new imported model.
     *
     * @param name the name of this mode, containing only characters in [A-Za-z0-9_]
     * @param source the source path (directory or file) of this model
     */
    public ImportedModel(String name, String source) {
        if ( ! nameRegexp.matcher(name).matches())
            throw new IllegalArgumentException("An imported model name can only contain [A-Za-z0-9_], but is '" + name + "'");
        this.name = name;
        this.source = source;
    }

    /** Returns the name of this model, which can only contain the characters in [A-Za-z0-9_] */
    public String name() { return name; }

    /** Returns the source path (directiry or file) of this model */
    public String source() { return source; }

    /** Returns an immutable map of the arguments ("Placeholders") of this */
    public Map<String, TensorType> arguments() { return Collections.unmodifiableMap(arguments); }

    /**
     * Returns an immutable map of the small constants of this.
     * These should have sizes up to a few kb at most, and correspond to constant
     * values given in the TensorFlow or ONNX source.
     */
    public Map<String, Tensor> smallConstants() { return Collections.unmodifiableMap(smallConstants); }

    /**
     * Returns an immutable map of the large constants of this.
     * These can have sizes in gigabytes and must be distributed to nodes separately from configuration.
     * For TensorFlow this corresponds to Variable files stored separately.
     */
    public Map<String, Tensor> largeConstants() { return Collections.unmodifiableMap(largeConstants); }

    /**
     * Returns an immutable map of the expressions of this - corresponding to graph nodes
     * which are not Inputs/Placeholders or Variables (which instead become respectively arguments and constants).
     * Note that only nodes recursively referenced by a placeholder/input are added.
     */
    public Map<String, RankingExpression> expressions() { return Collections.unmodifiableMap(expressions); }

    /**
     * Returns an immutable map of the functions that are part of this model.
     * Note that the functions themselves are *not* copies and *not* immutable - they must be copied before modification.
     */
    public Map<String, RankingExpression> functions() { return Collections.unmodifiableMap(functions); }

    /** Returns an immutable map of the functions that must be provided by the environment running this model */
    public Map<String, TensorType> requiredFunctions() { return Collections.unmodifiableMap(requiredFunctions); }

    /** Returns an immutable map of the signatures of this */
    public Map<String, Signature> signatures() { return Collections.unmodifiableMap(signatures); }

    /** Returns the given signature. If it does not already exist it is added to this. */
    Signature signature(String name) {
        return signatures.computeIfAbsent(name, Signature::new);
    }

    /** Convenience method for returning a default signature */
    Signature defaultSignature() { return signature(defaultSignatureName); }

    void argument(String name, TensorType argumentType) { arguments.put(name, argumentType); }
    void smallConstant(String name, Tensor constant) { smallConstants.put(name, constant); }
    void largeConstant(String name, Tensor constant) { largeConstants.put(name, constant); }
    void expression(String name, RankingExpression expression) { expressions.put(name, expression); }
    void function(String name, RankingExpression expression) { functions.put(name, expression); }
    void requiredFunction(String name, TensorType type) { requiredFunctions.put(name, type); }

    /**
     * Returns all the output expressions of this indexed by name. The names consist of one or two parts
     * separated by dot, where the first part is the signature name
     * if signatures are used, or the expression name if signatures are not used and there are multiple
     * expressions, and the second is the output name if signature names are used.
     */
    public List<Pair<String, RankingExpression>> outputExpressions() {
        List<Pair<String, RankingExpression>> expressions = new ArrayList<>();
        for (Map.Entry<String, Signature> signatureEntry : signatures().entrySet()) {
            for (Map.Entry<String, String> outputEntry : signatureEntry.getValue().outputs().entrySet())
                expressions.add(new Pair<>(signatureEntry.getKey() + "." + outputEntry.getKey(),
                                           expressions().get(outputEntry.getValue())));
            if (signatureEntry.getValue().outputs().isEmpty()) // fallback: Signature without outputs
                expressions.add(new Pair<>(signatureEntry.getKey(),
                                           expressions().get(signatureEntry.getKey())));
        }
        if (signatures().isEmpty()) { // fallback for models without signatures
            if (expressions().size() == 1) {
                Map.Entry<String, RankingExpression> singleEntry = this.expressions.entrySet().iterator().next();
                expressions.add(new Pair<>(singleEntry.getKey(), singleEntry.getValue()));
            }
            else {
                for (Map.Entry<String, RankingExpression> expressionEntry : expressions().entrySet()) {
                    expressions.add(new Pair<>(expressionEntry.getKey(), expressionEntry.getValue()));
                }
            }
        }
        return expressions;
    }

    /**
     * A signature is a set of named inputs and outputs, where the inputs maps to argument
     * ("placeholder") names+types, and outputs maps to expressions nodes.
     * Note that TensorFlow supports multiple signatures in their format, but ONNX has no explicit
     * concept of signatures. For now, we handle ONNX models as having a single signature.
     */
    public class Signature {

        private final String name;
        private final Map<String, String> inputs = new HashMap<>();
        private final Map<String, String> outputs = new HashMap<>();
        private final Map<String, String> skippedOutputs = new HashMap<>();
        private final List<String> importWarnings = new ArrayList<>();

        public Signature(String name) {
            this.name = name;
        }

        public String name() { return name; }

        /** Returns the result this is part of */
        public ImportedModel owner() { return ImportedModel.this; }

        /**
         * Returns an immutable map of the inputs (evaluation context) of this. This is a map from input name
         * to argument (Placeholder) name in the owner of this
         */
        public Map<String, String> inputs() { return Collections.unmodifiableMap(inputs); }

        /** Returns the type of the argument this input references */
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

        /** Returns the expression this output references */
        public RankingExpression outputExpression(String outputName) { return owner().expressions().get(outputs.get(outputName)); }

        @Override
        public String toString() { return "signature '" + name + "'"; }

        void input(String inputName, String argumentName) { inputs.put(inputName, argumentName); }
        void output(String name, String expressionName) { outputs.put(name, expressionName); }
        void skippedOutput(String name, String reason) { skippedOutputs.put(name, reason); }
        void importWarning(String warning) { importWarnings.add(warning); }

    }

}
