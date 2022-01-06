// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.operations;

import ai.vespa.rankingexpression.importer.DimensionRenamer;
import ai.vespa.rankingexpression.importer.OrderedTensorType;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.tensor.functions.TensorFunction;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class Constant extends IntermediateOperation {

    public Constant(String modelName, String nodeName, OrderedTensorType type) {
        super(modelName, nodeName, Collections.emptyList());
        this.type = type.rename(vespaName() + "_");
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        return type;
    }

    @Override
    protected TensorFunction<Reference> lazyGetFunction() {
        return null;  // will be added by function() since this is constant.
    }

    /**
     * Constant values are sent in via the constantValueFunction, as the
     * dimension names and thus the data layout depends on the dimension
     * renaming which happens after the conversion to intermediate graph.
     */
    @Override
    public Optional<Value> getConstantValue() {
        return Optional.ofNullable(constantValueFunction).map(func -> func.apply(type));
    }

    @Override
    public void addDimensionNameConstraints(DimensionRenamer renamer) {
        addConstraintsFrom(type, renamer);
    }

    @Override
    public boolean isConstant() {
        return true;
    }

    @Override
    public Constant withInputs(List<IntermediateOperation> inputs) {
        if ( ! inputs.isEmpty())
            throw new IllegalArgumentException("Constant cannot take inputs");
        Constant constant = new Constant(modelName(), name(), type);
        constant.setConstantValueFunction(constantValueFunction);
        return constant;
    }

    @Override
    public String operationName() { return "Constant"; }

    @Override
    public String toString() {
        return "Constant(" + type + ")";
    }

    @Override
    public String toFullString() {
        return "\t" + type + ":\tConstant(" + type + ")";
    }

}
