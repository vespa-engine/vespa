// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.language.Language;

/**
 * Gets the language previously set in the execution context.
 *
 * @author bratseth
 */
public final class GetLanguageExpression extends Expression {

    @Override
    public boolean requiresInput() { return false; }

    @Override
    public boolean isMutating() { return false; }

    @Override
    public DataType setInputType(DataType inputType, TypeContext context) {
        super.setInputType(inputType, context);
        return DataType.STRING;
    }

    @Override
    public DataType setOutputType(DataType outputType, TypeContext context) {
        return super.setOutputType(DataType.STRING, outputType, null, context);
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        context.setCurrentValue(new StringFieldValue(context.getLanguage().languageCode()));
    }

    @Override
    public String toString() { return "get_language"; }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof GetLanguageExpression)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
