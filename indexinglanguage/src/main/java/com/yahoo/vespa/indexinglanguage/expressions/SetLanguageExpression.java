// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.language.Language;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class SetLanguageExpression extends Expression {

    @Override
    protected void doExecute(ExecutionContext ctx) {
        ctx.setLanguage(Language.fromLanguageTag(String.valueOf(ctx.getValue())));
    }

    @Override
    protected void doVerify(VerificationContext ctx) {
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
