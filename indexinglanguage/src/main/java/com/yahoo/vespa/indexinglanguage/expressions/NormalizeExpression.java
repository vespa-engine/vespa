// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.language.Linguistics;
import com.yahoo.language.process.Transformer;

/**
 * @author Simon Thoresen Hult
 */
public class NormalizeExpression extends Expression {

    private final Linguistics linguistics;

    public NormalizeExpression(Linguistics linguistics) {
        this.linguistics = linguistics;
    }

    public Linguistics getLinguistics() {
        return linguistics;
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        Transformer transformer = linguistics.getTransformer();
        context.setValue(new StringFieldValue(transformer.accentDrop(String.valueOf(context.getValue()),
                                                                     context.resolveLanguage(linguistics))));
    }

    @Override
    protected void doVerify(VerificationContext context) {
        context.setValue(createdOutputType());
    }

    @Override
    public DataType requiredInputType() {
        return DataType.STRING;
    }

    @Override
    public DataType createdOutputType() {
        return DataType.STRING;
    }

    @Override
    public String toString() {
        return "normalize";
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof NormalizeExpression)) {
            return false;
        }
        NormalizeExpression rhs = (NormalizeExpression)obj;
        if (linguistics != rhs.linguistics) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
