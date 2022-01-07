// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.operations;

import com.yahoo.searchlib.rankingexpression.Reference;
import ai.vespa.rankingexpression.importer.OrderedTensorType;
import ai.vespa.rankingexpression.importer.DimensionRenamer;
import com.yahoo.tensor.evaluation.VariableTensor;
import com.yahoo.tensor.functions.Rename;
import com.yahoo.tensor.functions.TensorFunction;

import java.util.Collections;
import java.util.List;

public class Argument extends IntermediateOperation {

    private OrderedTensorType standardNamingType;  // using standard naming convention: d0, d1, ...

    public Argument(String modelName, String nodeName, OrderedTensorType type) {
        super(modelName, nodeName, Collections.emptyList());
        this.type = type.rename(vespaName() + "_");
        standardNamingType = OrderedTensorType.standardType(type);
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        return type;
    }

    @Override
    protected TensorFunction<Reference> lazyGetFunction() {
        TensorFunction<Reference> output = new VariableTensor<Reference>(vespaName(), standardNamingType.type());
        if ( ! standardNamingType.equals(type)) {
            List<String> renameFrom = standardNamingType.dimensionNames();
            List<String> renameTo = type.dimensionNames();
            output = new Rename<Reference>(output, renameFrom, renameTo);
        }
        return output;
    }

    @Override
    public void addDimensionNameConstraints(DimensionRenamer renamer) {
        for (int i = 0; i < type.dimensions().size(); i++) {
            renamer.addDimension(type.dimensions().get(i).name());

            //  Each dimension is distinct and ordered correctly:
            for (int j = i + 1; j < type.dimensions().size(); j++) {
                renamer.addConstraint(type.dimensions().get(i).name(), type.dimensions().get(j).name(),
                        DimensionRenamer.Constraint.lessThan(false),
                        this);
            }
        }
    }

    @Override
    public boolean isInput() {
        return true;
    }

    @Override
    public boolean isConstant() {
        return false;
    }

    @Override
    public Argument withInputs(List<IntermediateOperation> inputs) {
        if ( ! inputs.isEmpty())
            throw new IllegalArgumentException("Argument cannot take inputs");
        return new Argument(modelName(), name(), type);
    }

    @Override
    public String operationName() { return "Argument"; }

    @Override
    public String toString() { return "Argument(" + standardNamingType + ")"; }

    @Override
    public String toFullString() {
        return "\t" + type + ":\tArgument(" + standardNamingType + ")";
    }

}
