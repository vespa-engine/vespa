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
 * The result of importing a TensorFlow model into Vespa:
 * - A list of ranking expressions reproducing the computations of the outputs in the TensorFlow model
 * - A list of named constant tensors
 * - A list of expected input tensors, with their tensor type
 * - A list of warning messages
 *
 * @author bratseth
 */
// This object can be built incrementally within this package, but is immutable when observed from outside the package
// TODO: Retain signature structure in ImportResult (input + output-expression bundles)
public class ImportResult {

    private final List<RankingExpression> expressions = new ArrayList<>();
    private final Map<String, Tensor> constants = new HashMap<>();
    private final Map<String, TensorType> arguments = new HashMap<>();
    private final List<String> warnings = new ArrayList<>();

    void add(RankingExpression expression) { expressions.add(expression); }
    void set(String name, Tensor constant) { constants.put(name, constant); }
    void set(String name, TensorType argument) { arguments.put(name, argument); }
    void warn(String warning) { warnings.add(warning); }

    /** Returns an immutable list of the expressions of this */
    public List<RankingExpression> expressions() { return Collections.unmodifiableList(expressions); }

    /** Returns an immutable map of the constants of this */
    public Map<String, Tensor> constants() { return Collections.unmodifiableMap(constants); }

    /** Returns an immutable map of the arguments of this */
    public Map<String, TensorType> arguments() { return Collections.unmodifiableMap(arguments); }

    /** Returns an immutable list, in natural sort order of the warnings generated while importing this */
    public List<String> warnings() {
        return warnings.stream().sorted().collect(Collectors.toList());
    }

}
