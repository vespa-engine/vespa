// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
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

    public HashExpression() {
        super(DataType.STRING);
    }

    @Override
    public DataType setInputType(DataType inputType, VerificationContext context) {
        if ( inputType != DataType.STRING)
            throw new VerificationException(this, ": This require a string input, but got " + inputType);
        super.setInputType(inputType, context);
        return null; // Can not infer int or long
    }

    @Override
    public DataType setOutputType(DataType outputType, VerificationContext context) {
        super.setOutputType(outputType, context);
        if ( ! isHashCompatible(outputType))
            throw new VerificationException(this, ": This require the output to be int or long not " + outputType);
        return DataType.STRING;
    }

    @Override
    protected void doVerify(VerificationContext context) {
        context.setCurrentType(createdOutputType());
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        StringFieldValue input = (StringFieldValue) context.getCurrentValue();
        if (requireOutputType().equals(DataType.INT))
            context.setCurrentValue(new IntegerFieldValue(hashToInt(input.getString())));
        else if (requireOutputType().equals(DataType.LONG))
            context.setCurrentValue(new LongFieldValue(hashToLong(input.getString())));
        else
            throw new IllegalStateException(); // won't happen
    }

    private int hashToInt(String value) {
        return hasher.hashString(value, StandardCharsets.UTF_8).asInt();
    }

    private long hashToLong(String value) {
        return hasher.hashString(value, StandardCharsets.UTF_8).asLong();
    }

    private boolean isHashCompatible(DataType type) {
        if (type.equals(DataType.INT)) return true;
        if (type.equals(DataType.LONG)) return true;
        return false;
    }

    @Override
    public DataType createdOutputType() { return requireOutputType(); }

    @Override
    public String toString() { return "hash"; }

    @Override
    public int hashCode() { return 987; }

    @Override
    public boolean equals(Object o) { return o instanceof HashExpression; }

}
