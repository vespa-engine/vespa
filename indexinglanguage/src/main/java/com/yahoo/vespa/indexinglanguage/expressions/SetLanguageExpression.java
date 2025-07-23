// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.language.Language;

/**
 * Sets the language in the execution context.
 *
 * @author Simon Thoresen Hult
 */
public final class SetLanguageExpression extends Expression {

    @Override
    public boolean isMutating() { return false; }

    @Override
    public DataType setInputType(DataType inputType, TypeContext context) {
        return super.setInputType(inputType, DataType.STRING, context);
    }

    @Override
    public DataType setOutputType(DataType outputType, TypeContext context) {
        return super.setOutputType(outputType, context);
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        if (context.getCurrentValue() == null) context.setLanguage(Language.UNKNOWN);
        context.setLanguage(Language.fromLanguageTag(String.valueOf(context.getCurrentValue())));
    }

    @Override
    public String toString() { return "set_language"; }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SetLanguageExpression)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
