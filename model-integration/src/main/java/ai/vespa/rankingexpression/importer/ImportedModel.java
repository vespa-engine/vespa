// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer;

import com.google.common.collect.ImmutableMap;
import ai.vespa.rankingexpression.importer.configmodelview.ImportedMlFunction;
import ai.vespa.rankingexpression.importer.configmodelview.ImportedMlModel;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.io.IOUtils;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The result of importing an ML model into Vespa.
 *
 * @author bratseth
 */
public class ImportedModel implements ImportedMlModel {

    private static final String defaultSignatureName = "default";

    private static final Pattern nameRegexp = Pattern.compile("[A-Za-z0-9_]*");
    private final String name;
    private final String source;
    private final ModelType modelType;

    private final Map<String, Signature> signatures = new HashMap<>();
    private final Map<String, TensorType> inputs = new HashMap<>();
    private final Map<String, Tensor> smallConstants = new HashMap<>();
    private final Map<String, Tensor> largeConstants = new HashMap<>();
    private final Map<String, RankingExpression> expressions = new HashMap<>();
    private final Map<String, RankingExpression> functions = new HashMap<>();

    /**
     * Creates a new imported model.
     *
     * @param name the name of this mode, containing only characters in [A-Za-z0-9_]
     * @param source the source path (directory or file) of this model
     */
    public ImportedModel(String name, String source, ModelType modelType) {
        if ( ! nameRegexp.matcher(name).matches())
            throw new IllegalArgumentException("An imported model name can only contain [A-Za-z0-9_], but is '" + name + "'");
        this.name = name;
        this.source = source;
        this.modelType = modelType;
    }

    /** Returns the name of this model, which can only contain the characters in [A-Za-z0-9_] */
    @Override
    public String name() { return name; }

    /** Returns the source path (directory or file) of this model */
    @Override
    public String source() { return source; }

    /** Returns the original model type */
    @Override
    public ModelType modelType() { return modelType; }

    @Override
    public String toString() { return "imported model '" + name + "' from " + source; }

    /** Returns an immutable map of the inputs of this */
    public Map<String, TensorType> inputs() { return Collections.unmodifiableMap(inputs); }

    @Override
    public Optional<String> inputTypeSpec(String input) {
        return Optional.ofNullable(inputs.get(input)).map(TensorType::toString);
    }

    /**
     * Returns an immutable map of the small constants of this.
     * These should have sizes up to a few kb at most, and correspond to constant values given in the source model.
     */
    @Override
    public Map<String, Tensor> smallConstantTensors() { return Map.copyOf(smallConstants); }
    /**
     * Returns an immutable map of the small constants of this, represented as strings on the standard tensor form.
     * These should have sizes up to a few kb at most, and correspond to constant values given in the source model.
     * @deprecated Use smallConstantTensors instead
     */
    @Override
    @SuppressWarnings("removal")
    @Deprecated(forRemoval = true)
    public Map<String, String> smallConstants() { return asStrings(smallConstants); }

    boolean hasSmallConstant(String name) { return smallConstants.containsKey(name); }

    /**
     * Returns an immutable map of the large constants of this.
     * These can have sizes in gigabytes and must be distributed to nodes separately from configuration.
     */
    @Override
    public Map<String, Tensor> largeConstantTensors() { return Map.copyOf(largeConstants); }
    /**
     * Returns an immutable map of the large constants of this, represented as strings on the standard tensor form.
     * These can have sizes in gigabytes and must be distributed to nodes separately from configuration.
     * @deprecated Use largeConstantTensors instead
     */
    @Override
    @SuppressWarnings("removal")
    @Deprecated(forRemoval = true)
    public Map<String, String> largeConstants() { return asStrings(largeConstants); }

    boolean hasLargeConstant(String name) { return largeConstants.containsKey(name); }

    /**
     * Returns an immutable map of the expressions of this - corresponding to graph nodes
     * which are not Inputs/Placeholders or Variables (which instead become respectively inputs and constants).
     * Note that only nodes recursively referenced by a placeholder/input are added.
     */
    public Map<String, RankingExpression> expressions() { return Collections.unmodifiableMap(expressions); }

    /**
     * Returns an immutable map of the functions that are part of this model.
     */
    @Override
    public Map<String, String> functions() { return asExpressionStrings(functions); }

    /** Returns an immutable map of the signatures of this */
    public Map<String, Signature> signatures() { return Collections.unmodifiableMap(signatures); }

    /** Returns the given signature. If it does not already exist it is added to this. */
    public Signature signature(String name) {
        return signatures.computeIfAbsent(name, Signature::new);
    }

    /** Convenience method for returning a default signature */
    public Signature defaultSignature() { return signature(defaultSignatureName); }

    public void input(String name, TensorType argumentType) { inputs.put(name, argumentType); }
    public void smallConstant(String name, Tensor constant) { smallConstants.put(name, constant); }
    public void largeConstant(String name, Tensor constant) { largeConstants.put(name, constant); }
    public void expression(String name, RankingExpression expression) { expressions.put(name, expression); }
    public void function(String name, RankingExpression expression) { functions.put(name, expression); }

    public void expression(String name, String expression) {
        try {
            expression = expression.trim();
            if ( expression.startsWith("file:")) {
                String filePath = expression.substring("file:".length()).trim();
                if ( ! filePath.endsWith(ApplicationPackage.RANKEXPRESSION_NAME_SUFFIX))
                    filePath = filePath + ApplicationPackage.RANKEXPRESSION_NAME_SUFFIX;
                expression = IOUtils.readFile(relativeFile(filePath, "function '" + name + "'"));
            }
            expression(name, new RankingExpression(expression));
        }
        catch (IOException e) {
            throw new IllegalArgumentException("Could not read file referenced in '" + name + "'");
        }
        catch (ParseException e) {
            throw new IllegalArgumentException("Could not parse function '" + name + "'", e);
        }
    }

    /**
     * Returns a reference to the File at a path given relative to the source root of this model
     *
     * @throws IllegalArgumentException if the path is illegal or non-existent
     */
    public File relativeFile(String relativePath, String descriptionOfPath) {
        File file = new File(new File(source()).getParent(), relativePath);
        if ( ! file.exists())
            throw new IllegalArgumentException(descriptionOfPath + " references '" + relativePath +
                                               "', but this file does not exist");
        return file;
    }

    /**
     * Returns all the output expressions of this indexed by name. The names consist of one or two parts
     * separated by dot, where the first part is the signature name
     * if signatures are used, or the expression name if signatures are not used and there are multiple
     * expressions, and the second is the output name if signature names are used.
     */
    @Override
    public List<ImportedMlFunction> outputExpressions() {
        List<ImportedMlFunction> functions = new ArrayList<>();
        for (Map.Entry<String, Signature> signatureEntry : signatures().entrySet()) {
            for (Map.Entry<String, String> outputEntry : signatureEntry.getValue().outputs().entrySet())
                functions.add(signatureEntry.getValue().outputFunction(outputEntry.getKey(),
                                                                       signatureEntry.getKey() + "." + outputEntry.getKey()));
            if (signatureEntry.getValue().outputs().isEmpty()) // fallback: Signature without outputs
                functions.add(new ImportedMlFunction(signatureEntry.getKey(),
                                                     new ArrayList<>(signatureEntry.getValue().inputs().values()),
                                                     expressions().get(signatureEntry.getKey()).getRoot().toString(),
                                                     asStrings(signatureEntry.getValue().inputMap()),
                                                     Optional.empty()));
        }
        if (signatures().isEmpty()) { // fallback for models without signatures
            if (expressions().size() == 1) {
                Map.Entry<String, RankingExpression> singleEntry = this.expressions.entrySet().iterator().next();
                functions.add(new ImportedMlFunction(singleEntry.getKey(),
                                                     new ArrayList<>(inputs.keySet()),
                                                     singleEntry.getValue().getRoot().toString(),
                                                     asStrings(inputs),
                                                     Optional.empty()));
            }
            else {
                for (Map.Entry<String, RankingExpression> expressionEntry : expressions().entrySet()) {
                    functions.add(new ImportedMlFunction(expressionEntry.getKey(),
                                                         new ArrayList<>(inputs.keySet()),
                                                         expressionEntry.getValue().getRoot().toString(),
                                                         asStrings(inputs),
                                                         Optional.empty()));
                }
            }
        }
        return functions;
    }

    private Map<String, String> asStrings(Map<String, ?> map) {
        HashMap<String, String> values = new HashMap<>();
        for (Map.Entry<String, ?> entry : map.entrySet())
            values.put(entry.getKey(), entry.getValue().toString());
        return values;
    }

    private Map<String, String> asExpressionStrings(Map<String, RankingExpression> map) {
        HashMap<String, String> values = new HashMap<>();
        for (Map.Entry<String, RankingExpression> entry : map.entrySet())
            values.put(entry.getKey(), entry.getValue().getRoot().toString());
        return values;
    }

    @Override
    public boolean isNative() {
        return true;
    }

    @Override
    public ImportedModel asNative() {
        return this;
    }

    /**
     * A signature is a set of named inputs and outputs, where the inputs maps to input
     * ("placeholder") names+types, and outputs maps to expressions nodes.
     * Note that TensorFlow supports multiple signatures in their format, but ONNX has no explicit
     * concept of signatures. For now, we handle ONNX models as having a single signature.
     */
    public class Signature {

        private final String name;
        private final Map<String, String> inputs = new LinkedHashMap<>();
        private final Map<String, String> outputs = new LinkedHashMap<>();
        private final Map<String, String> skippedOutputs = new HashMap<>();

        Signature(String name) {
            this.name = name;
        }

        public String name() { return name; }

        /** Returns the result this is part of */
        ImportedModel owner() { return ImportedModel.this; }

        /**
         * Returns an immutable map of the inputs (evaluation context) of this. This is a map from input name
         * in this signature to input name in the owning model
         */
        public Map<String, String> inputs() { return Collections.unmodifiableMap(inputs); }

        /** Returns the name and type of all inputs in this signature as an immutable map */
        Map<String, TensorType> inputMap() {
            ImmutableMap.Builder<String, TensorType> inputs = new ImmutableMap.Builder<>();
            // Note: We're naming inputs by their actual name (used in the expression, given by what the input maps *to*
            // in the model, as these are the names which must actually be bound, if we are to avoid creating an
            // "input mapping" to accommodate this complexity
            for (Map.Entry<String, String> inputEntry : inputs().entrySet())
                inputs.put(inputEntry.getValue(), owner().inputs().get(inputEntry.getValue()));
            return inputs.build();
        }

        /** Returns the type of the input this input references */
        public TensorType inputArgument(String inputName) { return owner().inputs().get(inputs.get(inputName)); }

        /** Returns an immutable list of the expression names of this */
        public Map<String, String> outputs() { return Collections.unmodifiableMap(outputs); }

        /**
         * Returns an immutable list of the outputs of this which could not be imported,
         * with a string detailing the reason for each
         */
        public Map<String, String> skippedOutputs() { return Collections.unmodifiableMap(skippedOutputs); }

        /** Returns the expression this output references as an imported function */
        public ImportedMlFunction outputFunction(String outputName, String functionName) {
            RankingExpression outputExpression = owner().expressions().get(outputs.get(outputName));
            if (outputExpression == null)
                throw new IllegalArgumentException("Missing output '" + outputName + "' in " + this);
            return new ImportedMlFunction(functionName,
                                          new ArrayList<>(inputs.values()),
                                          outputExpression.getRoot().toString(),
                                          asStrings(inputMap()),
                                          Optional.empty());
        }

        @Override
        public String toString() { return "signature '" + name + "'"; }

        void input(String inputName, String argumentName) { inputs.put(inputName, argumentName); }
        void output(String name, String expressionName) { outputs.put(name, expressionName); }
        void skippedOutput(String name, String reason) { skippedOutputs.put(name, reason); }

    }

}
