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
import com.yahoo.language.process.Embedder;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Embeds a string in a tensor space using the configured Embedder component
 *
 * @author bratseth
 */
public class EmbedExpression extends Expression  {

    private final Embedder embedder;
    private final String embedderId;
    private final List<String> embedderArguments;

    /** The destination the embedding will be written to on the form [schema name].[field name] */
    private String destination;

    /** The target type we are embedding into. */
    private TensorType targetType;

    public EmbedExpression(Map<String, Embedder> embedders, String embedderId, List<String> embedderArguments) {
        super(null);
        this.embedderId = embedderId;
        this.embedderArguments = List.copyOf(embedderArguments);

        boolean embedderIdProvided = embedderId != null && !embedderId.isEmpty();

        if (embedders.size() == 0) {
            throw new IllegalStateException("No embedders provided");  // should never happen
        }
        else if (embedders.size() == 1 && ! embedderIdProvided) {
            this.embedder = embedders.entrySet().stream().findFirst().get().getValue();
        }
        else if (embedders.size() > 1 && ! embedderIdProvided) {
            this.embedder = new Embedder.FailingEmbedder("Multiple embedders are provided but no embedder id is given. " +
                                                         "Valid embedders are " + validEmbedders(embedders));
        }
        else if ( ! embedders.containsKey(embedderId)) {
            this.embedder = new Embedder.FailingEmbedder("Can't find embedder '" + embedderId + "'. " +
                                                         "Valid embedders are " + validEmbedders(embedders));
        } else  {
            this.embedder = embedders.get(embedderId);
        }
    }

    @Override
    public void setStatementOutput(DocumentType documentType, Field field) {
        targetType = toTargetTensor(field.getDataType());
        destination = documentType.getName() + "." + field.getName();
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        if (context.getValue() == null) return;
        Tensor output;
        if (context.getValue().getDataType() == DataType.STRING) {
            output = embedSingleValue(context);
        }
        else if (context.getValue().getDataType() instanceof ArrayDataType &&
                 ((ArrayDataType)context.getValue().getDataType()).getNestedType() == DataType.STRING) {
            output = embedArrayValue(context);
        }
        else {
            throw new IllegalArgumentException("Embedding can only be done on string or string array fields, not " +
                                               context.getValue().getDataType());
        }
        context.setValue(new TensorFieldValue(output));
    }

    private Tensor embedSingleValue(ExecutionContext context) {
        StringFieldValue input = (StringFieldValue)context.getValue();
        return embed(input.getString(), targetType, context);
    }

    @SuppressWarnings("unchecked")
    private Tensor embedArrayValue(ExecutionContext context) {
        var input = (Array<StringFieldValue>)context.getValue();
        var builder = Tensor.Builder.of(targetType);
        if (targetType.rank() == 2)
            embedArrayValueToRank2Tensor(input, builder, context);
        else
            embedArrayValueToRank3Tensor(input, builder, context);
        return builder.build();
    }

    private void embedArrayValueToRank2Tensor(Array<StringFieldValue> input,
                                              Tensor.Builder builder,
                                              ExecutionContext context) {
        String mappedDimension = targetType.mappedSubtype().dimensions().get(0).name();
        String indexedDimension = targetType.indexedSubtype().dimensions().get(0).name();
        for (int i = 0; i < input.size(); i++) {
            Tensor tensor = embed(input.get(i).getString(), targetType.indexedSubtype(), context);
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
        String outerMappedDimension = embedderArguments.get(0);
        String innerMappedDimension = targetType.mappedSubtype().dimensionNames().stream().filter(d -> !d.equals(outerMappedDimension)).findFirst().get();
        String indexedDimension = targetType.indexedSubtype().dimensions().get(0).name();
        long indexedDimensionSize = targetType.indexedSubtype().dimensions().get(0).size().get();
        var innerType = new TensorType.Builder().mapped(innerMappedDimension).indexed(indexedDimension,indexedDimensionSize).build();
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

    private Tensor embed(String input, TensorType targetType, ExecutionContext context) {
        return embedder.embed(input,
                              new Embedder.Context(destination).setLanguage(context.getLanguage()).setEmbedderId(embedderId),
                              targetType);

    }

    @Override
    protected void doVerify(VerificationContext context) {
        String outputField = context.getOutputField();
        if (outputField == null)
            throw new VerificationException(this, "No output field in this statement: " +
                                                  "Don't know what tensor type to embed into");
        targetType = toTargetTensor(context.getInputType(this, outputField));
        if ( ! validTarget(targetType))
            throw new VerificationException(this, "The embedding target field must either be a dense 1d tensor, a mapped 1d tensor," +
                                                  "an array of dense 1d tensors, or a mixed 2d or 3d tensor");
        if (targetType.rank() == 3) {
            if (embedderArguments.size() != 1)
                throw new VerificationException(this, "When the embedding target field is a 3d tensor " +
                                                      "the name of the tensor dimension that corresponds to the input array elements must " +
                                                      "be given as a second argument to embed, e.g: ... | embed colbert paragraph | ...");
            if ( ! targetType.mappedSubtype().dimensionNames().contains(embedderArguments.get(0)))
                throw new VerificationException(this, "The dimension '" + embedderArguments.get(0) + "' given to embed " +
                                                      "is not a sparse dimension of the target type " + targetType);
        }

        context.setValueType(createdOutputType());
    }

    @Override
    public DataType createdOutputType() {
        return new TensorDataType(targetType);
    }

    private static TensorType toTargetTensor(DataType dataType) {
        if (dataType instanceof ArrayDataType) return toTargetTensor(((ArrayDataType) dataType).getNestedType());
        if  ( ! ( dataType instanceof TensorDataType))
            throw new IllegalArgumentException("Expected a tensor data type but got " + dataType);
        return ((TensorDataType)dataType).getTensorType();
    }

    private boolean validTarget(TensorType target) {
        if (target.rank() == 1) // indexed or mapped 1d tensor
            return true;
        if (target.rank() == 2 && target.indexedSubtype().rank() == 1)
            return true; // mixed 2d tensor
        if (target.rank() == 3 && target.indexedSubtype().rank() == 1)
            return true; // mixed 3d tensor
        return false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("embed");
        if (this.embedderId != null && !this.embedderId.isEmpty())
            sb.append(" ").append(this.embedderId);
        embedderArguments.forEach(arg -> sb.append(" ").append(arg));
        return sb.toString();
    }

    @Override
    public int hashCode() { return Objects.hash(embedder.hashCode(), embedder, embedderArguments); }

    @Override
    public boolean equals(Object o) {
        if ( ! super.equals(o)) return false;
        if ( ! (o instanceof EmbedExpression other)) return false;
        if ( ! Objects.equals(embedder, other.embedder)) return false;
        if ( ! Objects.equals(embedderArguments, other.embedderArguments)) return false;
        return true;
    }

    private static String validEmbedders(Map<String, Embedder> embedders) {
        List<String> embedderIds = new ArrayList<>();
        embedders.forEach((key, value) -> embedderIds.add(key));
        embedderIds.sort(null);
        return String.join(",", embedderIds);
    }

}
