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
import com.yahoo.language.process.Chunker;
import com.yahoo.language.process.Embedder;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;


/**
 * Splits a string into multiple elements
 *
 * @author bratseth
 */
public class ChunkExpression extends Expression  {

    private final SelectedComponent<Chunker> chunker;

    /** The destination the chunks will be written to on the form [schema name].[field name] */
    private String destination;

    public ChunkExpression(Map<String, Chunker> chunkers, String chunkerId,
                           List<String> chunkerArguments) {
        chunker = new SelectedComponent<>("chunker", chunkers, chunkerId, false,
                                          chunkerArguments, Chunker.FailingChunker::new);
    }

    @Override
    public DataType setInputType(DataType inputType, TypeContext context) {
        super.setInputType(inputType, DataType.STRING, context);
        return ArrayDataType.getArray(DataType.STRING);
    }

    @Override
    public DataType setOutputType(DataType outputType, TypeContext context) {
        super.setOutputType(ArrayDataType.getArray(DataType.STRING), outputType, null, context);
        return DataType.STRING;
    }

    @Override
    public void setStatementOutput(DocumentType documentType, Field field) {
        destination = documentType.getName() + "." + field.getName();
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        String input = String.valueOf(context.getCurrentValue());
        Array<StringFieldValue> output = new Array<>(DataType.getArray(DataType.STRING));
        if (!input.isEmpty()) {
            var chunkContext = new Chunker.Context(destination, chunker.arguments(), context.getCache());
            for (Chunker.Chunk chunk : chunker.component().chunk(input, chunkContext)) {
                output.add(new StringFieldValue(chunk.text()));
            }
        }
        context.setCurrentValue(output);
    }

    @Override
    public String toString() {
        return "chunk" + chunker.argumentsString();
    }

    @Override
    public int hashCode() { return Objects.hash(ChunkExpression.class, chunker); }

    @Override
    public boolean equals(Object o) {
        if ( ! (o instanceof ChunkExpression other)) return false;
        if ( ! other.chunker.equals(this.chunker)) return false;
        return true;
    }

}
