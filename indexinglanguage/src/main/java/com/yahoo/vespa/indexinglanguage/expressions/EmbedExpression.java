// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.Field;
import com.yahoo.document.TensorDataType;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.language.process.Embedder;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Embeds a string in a tensor space using the configured Embedder component
 *
 * @author bratseth
 */
public class EmbedExpression extends Expression  {

    private final Embedder embedder;
    private final String embedderId;

    /** The destination the embedding will be written to on the form [schema name].[field name] */
    private String destination;

    /** The target type we are embedding into. */
    private TensorType targetType;

    public EmbedExpression(Map<String, Embedder> embedders, String embedderId) {
        super(DataType.STRING);
        this.embedderId = embedderId;

        boolean embedderIdProvided = embedderId != null && embedderId.length() > 0;

        if (embedders.size() == 0) {
            throw new IllegalStateException("No embedders provided");  // should never happen
        }
        else if (embedders.size() > 1 && ! embedderIdProvided) {
            this.embedder = new Embedder.FailingEmbedder("Multiple embedders are provided but no embedder id is given. " +
                    "Valid embedders are " + validEmbedders(embedders));
        }
        else if (embedders.size() == 1 && ! embedderIdProvided) {
            this.embedder = embedders.entrySet().stream().findFirst().get().getValue();
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
        StringFieldValue input = (StringFieldValue) context.getValue();
        Tensor tensor = embedder.embed(input.getString(),
                                       new Embedder.Context(destination).setLanguage(context.getLanguage()),
                                       targetType);
        context.setValue(new TensorFieldValue(tensor));
    }

    @Override
    protected void doVerify(VerificationContext context) {
        String outputField = context.getOutputField();
        if (outputField == null)
            throw new VerificationException(this, "No output field in this statement: " +
                                                  "Don't know what tensor type to embed into.");
        targetType = toTargetTensor(context.getInputType(this, outputField));
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

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("embed");
        if (this.embedderId != null && this.embedderId.length() > 0) {
            sb.append(" ").append(this.embedderId);
        }
        return sb.toString();
    }

    @Override
    public int hashCode() { return 1; }

    @Override
    public boolean equals(Object o) { return o instanceof EmbedExpression; }

    private static String validEmbedders(Map<String, Embedder> embedders) {
        List<String> embedderIds = new ArrayList<>();
        embedders.forEach((key, value) -> embedderIds.add(key));
        embedderIds.sort(null);
        return String.join(",", embedderIds);
    }

}
