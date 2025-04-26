// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.Field;
import com.yahoo.document.TensorDataType;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.language.Linguistics;
import com.yahoo.language.process.Embedder;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.Iterator;
import java.util.List;
import java.util.Map;


/**
 * Embeds a string in a tensor space using the configured Embedder component
 *
 * @author bratseth
 */
public class EmbedExpression extends Expression  {

    private final Linguistics linguistics;
    private final SelectedComponent<Embedder> embedder;

    /** The destination the embedding will be written to on the form [schema name].[field name] */
    private String destination;

    public EmbedExpression(Linguistics linguistics, Map<String, Embedder> embedders, String embedderId, List<String> embedderArguments) {
        this.linguistics = linguistics;
        embedder = new SelectedComponent<>("embedder", embedders, embedderId, embedderArguments,
                                           Embedder.FailingEmbedder::new);
    }

    @Override
    public DataType setInputType(DataType inputType, TypeContext context) {
        super.setInputType(inputType, context);
        var outputType = getOutputType(context); // Cannot be determined from input
        validateInputAndOutput(inputType, outputType);
        return outputType;
    }

    @Override
    public DataType setOutputType(DataType outputType, TypeContext context) {
        super.setOutputType(null, outputType, TensorDataType.any(), context);
        var inputType = getInputType(context); // Cannot be determined from output
        validateInputAndOutput(inputType, outputType);
        return inputType;
    }

    private void validateInputAndOutput(DataType input, DataType output) {
        if (input != null) {
            if (! (input.isAssignableTo(DataType.STRING)) &&
                ! (input instanceof ArrayDataType array && array.getNestedType().isAssignableTo(DataType.STRING)))
                invalid("This requires either a string or array<string> input type, but got " + input.getName());
        }
        if (output != null) {
            var outputTensor = toTargetTensor(output);
            if ( ! validTarget(outputTensor))
                invalid("The embedding target field must either be a dense 1d tensor, a mapped 1d tensor, a mapped 2d tensor, " +
                        "an array of dense 1d tensors, or a mixed 2d or 3d tensor");
            if (outputTensor.rank() == 2 && outputTensor.mappedSubtype().rank() == 2) {
                if (embedder.arguments().size() != 1)
                    invalid("When the embedding target field is a 2d mapped tensor " +
                            "the name of the tensor dimension that corresponds to the input array elements must " +
                            "be given as a second argument to embed, e.g: ... | embed splade paragraph | ...");
                if ( ! outputTensor.mappedSubtype().dimensionNames().contains(embedder.arguments().get(0))) {
                    invalid("The dimension '" + embedder.arguments().get(0) + "' given to embed " +
                            "is not a sparse dimension of the target type " + outputTensor);

                }
            }
            if (outputTensor.rank() == 3) {
                if (embedder.arguments().size() != 1)
                    invalid("When the embedding target field is a 3d tensor " +
                            "the name of the tensor dimension that corresponds to the input array elements must " +
                            "be given as a second argument to embed, e.g: ... | embed colbert paragraph | ...");
                if ( ! outputTensor.mappedSubtype().dimensionNames().contains(embedder.arguments().get(0)))
                    invalid("The dimension '" + embedder.arguments().get(0) + "' given to embed " +
                            "is not a sparse dimension of the target type " + outputTensor);
            }
        }
        if (input != null && output != null) { // verify input/output consistency
            var outputTensor = toTargetTensor(output);
            if (input.isAssignableTo(DataType.STRING)
                && !(outputTensor.rank() == 1 || (outputTensor.rank() == 2 && outputTensor.mappedSubtype().rank() > 0)))
                invalid("Input is a string, so output must be a rank 1 tensor, or a rank 2 tensor with " +
                        "one mapped dimension, but got " + outputTensor);
            if ((input instanceof ArrayDataType)
                && !(outputTensor.rank() > 1 && outputTensor.mappedSubtype().rank() > 0))
                invalid("Input is an array, so output must be a rank 2 or 3 tensor with " +
                        "at least one mapped dimension, but got " + outputTensor);
        }
    }

    private void invalid(String message) {
        throw new VerificationException(this, message);
    }

    @Override
    public void setStatementOutput(DocumentType documentType, Field field) {
        destination = documentType.getName() + "." + field.getName();
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        if (context.getCurrentValue() == null) return;
        Tensor output;
        if (context.getCurrentValue().getDataType() == DataType.STRING) {
            output = embedSingleValue(context);
        }
        else if (context.getCurrentValue().getDataType() instanceof ArrayDataType arrayType
                 && arrayType.getNestedType() == DataType.STRING) {
            output = embedArrayValue(getOutputTensorType(), context);
        }
        else {
            throw new IllegalArgumentException("Embedding can only be done on string or string array fields, not " +
                                               context.getCurrentValue().getDataType());
        }
        context.setCurrentValue(new TensorFieldValue(output));
    }

    private Tensor embedSingleValue(ExecutionContext context) {
        StringFieldValue input = (StringFieldValue)context.getCurrentValue();
        return embed(input.getString(), getOutputTensorType(), context);
    }

    @SuppressWarnings("unchecked")
    private Tensor embedArrayValue(TensorType targetType, ExecutionContext context) {
        var input = (Array<StringFieldValue>)context.getCurrentValue();
        var builder = Tensor.Builder.of(targetType);
        if (targetType.rank() == 2)
            if (targetType.indexedSubtype().rank() == 1)
                embedArrayValueToRank2Tensor(input, builder, context);
            else if(targetType.mappedSubtype().rank() == 2)
                embedArrayValueToRank2MappedTensor(input, builder, context);
            else
                throw new IllegalArgumentException("Embedding an array into " + targetType + " is not supported");
        else
            embedArrayValueToRank3Tensor(input, builder, context);
        return builder.build();
    }

    private void embedArrayValueToRank2Tensor(Array<StringFieldValue> input,
                                              Tensor.Builder builder,
                                              ExecutionContext context) {
        String mappedDimension = builder.type().mappedSubtype().dimensions().get(0).name();
        String indexedDimension = builder.type().indexedSubtype().dimensions().get(0).name();
        for (int i = 0; i < input.size(); i++) {
            Tensor tensor = embed(input.get(i).getString(), builder.type().indexedSubtype(), context);
            for (Iterator<Tensor.Cell> cells = tensor.cellIterator(); cells.hasNext(); ) {
                Tensor.Cell cell = cells.next();
                builder.cell()
                       .label(mappedDimension, i)
                       .label(indexedDimension, cell.getKey().numericLabel(0))
                       .value(cell.getValue());
            }
        }
    }

    private void embedArrayValueToRank3Tensor(Array<StringFieldValue> input,
                                              Tensor.Builder builder,
                                              ExecutionContext context) {
        String outerMappedDimension = embedder.arguments().get(0);
        String innerMappedDimension = builder.type().mappedSubtype().dimensionNames().stream().filter(d -> !d.equals(outerMappedDimension)).findFirst().get();
        String indexedDimension = builder.type().indexedSubtype().dimensions().get(0).name();
        long indexedDimensionSize = builder.type().indexedSubtype().dimensions().get(0).size().get();
        var innerType = new TensorType.Builder(builder.type().valueType()).mapped(innerMappedDimension).indexed(indexedDimension,indexedDimensionSize).build();
        int innerMappedDimensionIndex = innerType.indexOfDimensionAsInt(innerMappedDimension);
        int indexedDimensionIndex = innerType.indexOfDimensionAsInt(indexedDimension);
        for (int i = 0; i < input.size(); i++) {
            Tensor tensor = embed(input.get(i).getString(), innerType, context);
            for (Iterator<Tensor.Cell> cells = tensor.cellIterator(); cells.hasNext(); ) {
                Tensor.Cell cell = cells.next();
                builder.cell()
                       .label(outerMappedDimension, i)
                       .label(innerMappedDimension, cell.getKey().label(innerMappedDimensionIndex))
                       .label(indexedDimension, cell.getKey().numericLabel(indexedDimensionIndex))
                       .value(cell.getValue());
            }
        }
    }

    private void embedArrayValueToRank2MappedTensor(Array<StringFieldValue> input,
                                              Tensor.Builder builder,
                                              ExecutionContext context) {
        String outerMappedDimension = embedder.arguments().get(0);
        String innerMappedDimension = getOutputTensorType().mappedSubtype().dimensionNames().stream().filter(d -> !d.equals(outerMappedDimension)).findFirst().get();

        var innerType = new TensorType.Builder(getOutputTensorType().valueType()).mapped(innerMappedDimension).build();
        int innerMappedDimensionIndex = innerType.indexOfDimensionAsInt(innerMappedDimension);

        for (int i = 0; i < input.size(); i++) {
            Tensor tensor = embed(input.get(i).getString(), innerType, context);
            for (Iterator<Tensor.Cell> cells = tensor.cellIterator(); cells.hasNext(); ) {
                Tensor.Cell cell = cells.next();
                builder.cell()
                        .label(outerMappedDimension, i)
                        .label(innerMappedDimension, cell.getKey().label(innerMappedDimensionIndex))
                        .value(cell.getValue());
            }
        }
    }

    private Tensor embed(String input, TensorType targetType, ExecutionContext context) {
        return embedder.component().embed(input,
                                          new Embedder.Context(destination, context.getCache()).setLanguage(context.resolveLanguage(linguistics))
                                                                                               .setEmbedderId(embedder.id()),
                                          targetType);
    }

    private TensorType getOutputTensorType() {
        return ((TensorDataType)getOutputType()).getTensorType();
    }

    private static TensorType toTargetTensor(DataType dataType) {
        if (dataType instanceof ArrayDataType) return toTargetTensor(dataType.getNestedType());
        if  ( ! ( dataType instanceof TensorDataType))
            throw new IllegalArgumentException("Expected a tensor data type but got " + dataType);
        return ((TensorDataType)dataType).getTensorType();
    }

    private boolean validTarget(TensorType target) {
        if (target.rank() == 1) // indexed or mapped 1d tensor
            return true;
        if (target.rank() == 2 && target.indexedSubtype().rank() == 1)
            return true; // mixed 2d tensor
        if(target.rank() == 2 && target.mappedSubtype().rank() == 2)
            return true; // mapped 2d tensor
        if (target.rank() == 3 && target.indexedSubtype().rank() == 1)
            return true; // mixed 3d tensor
        return false;
    }

    @Override
    public String toString() {
        return "embed" + embedder.argumentsString();
    }

    @Override
    public int hashCode() { return EmbedExpression.class.hashCode(); }

    @Override
    public boolean equals(Object o) {
        return o instanceof EmbedExpression;
    }

}
