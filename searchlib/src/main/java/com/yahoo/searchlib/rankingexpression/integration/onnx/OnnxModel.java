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
 * @author lesters
 */
public class OnnxModel {

    public OnnxModel(String outputNode) {
        this.output = outputNode;
    }

    private final String output;
    private final Map<String, TensorType> arguments = new HashMap<>();
    private final Map<String, Tensor> smallConstants = new HashMap<>();
    private final Map<String, Tensor> largeConstants = new HashMap<>();
    private final Map<String, RankingExpression> expressions = new HashMap<>();
    private final Map<String, TensorType> requiredMacros = new HashMap<>();

    void argument(String name, TensorType argumentType) { arguments.put(name, argumentType); }
    void smallConstant(String name, Tensor constant) { smallConstants.put(name, constant); }
    void largeConstant(String name, Tensor constant) { largeConstants.put(name, constant); }
    void expression(String name, RankingExpression expression) { expressions.put(name, expression); }
    void requiredMacro(String name, TensorType type) { requiredMacros.put(name, type); }

    /** Return the name of the output node for this model */
    public String output() { return output; }

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

    /** Returns an immutable map of the macros that must be provided by the environment running this model */
    public Map<String, TensorType> requiredMacros() { return Collections.unmodifiableMap(requiredMacros); }

}
