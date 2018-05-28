package com.yahoo.searchlib.rankingexpression.integration.onnx;

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
 * The result of importing an ONNX model into Vespa.
 *
 * @author bratseth
 * @author lesters
 */
public class OnnxModel {

    private static final Pattern nameRegexp = Pattern.compile("[A-Za-z0-9_]*");

    private final String name;

    public OnnxModel(String name) {
        if ( ! nameRegexp.matcher(name).matches())
            throw new IllegalArgumentException("A TensorFlow model name can only contain [A-Za-z0-9_], but is '" +
                    name + "'");
        this.name = name;
    }

    /** Returns the name of this model, which can only contain the characters in [A-Za-z0-9_] */
    public String name() { return name; }

    private final Map<String, String> inputs = new HashMap<>();
    private final Map<String, String> outputs = new HashMap<>();
    private final Map<String, String> skippedOutputs = new HashMap<>();
    private final List<String> importWarnings = new ArrayList<>();

    private final Map<String, TensorType> arguments = new HashMap<>();
    private final Map<String, Tensor> smallConstants = new HashMap<>();
    private final Map<String, Tensor> largeConstants = new HashMap<>();
    private final Map<String, RankingExpression> expressions = new HashMap<>();
    private final Map<String, RankingExpression> macros = new HashMap<>();
    private final Map<String, TensorType> requiredMacros = new HashMap<>();

    void input(String inputName, String argumentName) { inputs.put(inputName, argumentName); }
    void output(String name, String expressionName) { outputs.put(name, expressionName); }
    void skippedOutput(String name, String reason) { skippedOutputs.put(name, reason); }
    void importWarning(String warning) { importWarnings.add(warning); }
    void argument(String name, TensorType argumentType) { arguments.put(name, argumentType); }
    void smallConstant(String name, Tensor constant) { smallConstants.put(name, constant); }
    void largeConstant(String name, Tensor constant) { largeConstants.put(name, constant); }
    void expression(String name, RankingExpression expression) { expressions.put(name, expression); }
    void macro(String name, RankingExpression expression) { macros.put(name, expression); }
    void requiredMacro(String name, TensorType type) { requiredMacros.put(name, type); }

    /**
     * Returns an immutable map of the inputs (evaluation context) of this. This is a map from input name
     * to argument (Placeholder) name in the owner of this
     */
    public Map<String, String> inputs() { return Collections.unmodifiableMap(inputs); }

    /** Returns arguments().get(inputs.get(name)), e.g the type of the argument this input references */
    public TensorType inputArgument(String inputName) { return arguments().get(inputs.get(inputName)); }

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

    /** Returns expressions().get(outputs.get(outputName)), e.g the expression this output references */
    public RankingExpression outputExpression(String outputName) { return expressions().get(outputs.get(outputName)); }

    /** Returns an immutable map of the arguments (inputs) of this */
    public Map<String, TensorType> arguments() { return Collections.unmodifiableMap(arguments); }

    /**
     * Returns an immutable map of the small constants of this.
     */
    public Map<String, Tensor> smallConstants() { return Collections.unmodifiableMap(smallConstants); }

    /**
     * Returns an immutable map of the large constants of this.
     */
    public Map<String, Tensor> largeConstants() { return Collections.unmodifiableMap(largeConstants); }

    /**
     * Returns an immutable map of the expressions of this - corresponding to ONNX nodes
     * which are not inputs or constants.
     */
    public Map<String, RankingExpression> expressions() { return Collections.unmodifiableMap(expressions); }

    /** Returns an immutable map of macros that are part of this model */
    public Map<String, RankingExpression> macros() { return Collections.unmodifiableMap(macros); }

    /** Returns an immutable map of the macros that must be provided by the environment running this model */
    public Map<String, TensorType> requiredMacros() { return Collections.unmodifiableMap(requiredMacros); }

}
