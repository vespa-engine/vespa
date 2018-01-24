// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.language.Language;

/**
 * Sets the language in the execution context.
 *
 * @author Simon Thoresen
 */
public class SetLanguageExpression extends Expression {

    @Override
    protected void doExecute(ExecutionContext ctx) {
        ctx.setLanguage(Language.fromLanguageTag(String.valueOf(ctx.getValue())));
    }

    @Override
    protected void doVerify(VerificationContext context) {
        // empty
    }

    @Override
    public DataType requiredInputType() {
        return DataType.STRING;
    }

    @Override
    public DataType createdOutputType() {
        return null;
    }

    @Override
    public String toString() {
        return "set_language";
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SetLanguageExpression)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
