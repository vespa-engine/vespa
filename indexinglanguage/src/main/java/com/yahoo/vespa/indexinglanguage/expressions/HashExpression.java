// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.Field;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.LongFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;

import java.nio.charset.StandardCharsets;

/**
 * Hashes a string value to a long or int (by type inference on the target value).
 *
 * @author bratseth
 */
public class HashExpression extends Expression  {

    private final HashFunction hasher = Hashing.sipHash24();

    /** The target *primitive* type we are hashing into. */
    private DataType targetType;

    public HashExpression() {
        super(DataType.STRING);
    }

    @Override
    public void setStatementOutput(DocumentType documentType, Field field) {
        if ( ! canStoreHash(field.getDataType()))
            throw new IllegalArgumentException("Cannot use the hash function on an indexing statement for " +
                                               field.getName() +
                                               ": The hash function can only be used when the target field " +
                                               "is int or long or an array of int or long, not " + field.getDataType());
        targetType = primitiveTypeOf(field.getDataType());
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        StringFieldValue input = (StringFieldValue) context.getValue();
        if (targetType.equals(DataType.INT))
            context.setValue(new IntegerFieldValue(hashToInt(input.getString())));
        else if (targetType.equals(DataType.LONG))
            context.setValue(new LongFieldValue(hashToLong(input.getString())));
        else
            throw new IllegalStateException(); // won't happen
    }

    private int hashToInt(String value) {
        return hasher.hashString(value, StandardCharsets.UTF_8).asInt();
    }

    private long hashToLong(String value) {
        return hasher.hashString(value, StandardCharsets.UTF_8).asLong();
    }

    @Override
    protected void doVerify(VerificationContext context) {
        String outputField = context.getOutputField();
        if (outputField == null)
            throw new VerificationException(this, "No output field in this statement: " +
                                                  "Don't know what value to hash to.");
        DataType outputFieldType = context.getInputType(this, outputField);
        if ( ! canStoreHash(outputFieldType))
            throw new VerificationException(this, "The type of the output field " + outputField +
                                                  " is not int or long but " + outputFieldType);
        targetType = primitiveTypeOf(outputFieldType);
        context.setValueType(createdOutputType());
    }

    private boolean canStoreHash(DataType type) {
        if (type.equals(DataType.INT)) return true;
        if (type.equals(DataType.LONG)) return true;
        if (type instanceof ArrayDataType) return canStoreHash(((ArrayDataType)type).getNestedType());
        return false;
    }

    private static DataType primitiveTypeOf(DataType type) {
        if (type instanceof ArrayDataType) return ((ArrayDataType)type).getNestedType();
        return type;
    }

    @Override
    public DataType createdOutputType() { return targetType; }

    @Override
    public String toString() { return "hash"; }

    @Override
    public int hashCode() { return 987; }

    @Override
    public boolean equals(Object o) { return o instanceof HashExpression; }

}
