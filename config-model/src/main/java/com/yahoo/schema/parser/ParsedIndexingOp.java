// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.parser;

import com.yahoo.vespa.indexinglanguage.ExpressionSearcher;
import com.yahoo.vespa.indexinglanguage.expressions.AttributeExpression;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.IndexExpression;
import com.yahoo.vespa.indexinglanguage.expressions.LowerCaseExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ScriptExpression;
import com.yahoo.vespa.indexinglanguage.expressions.SummaryExpression;

/**
 * This class wraps an indexing script expression, with some helper
 * methods for extracting information from it
 * @author arnej27959
 **/
class ParsedIndexingOp {

    private final ScriptExpression script;

    ParsedIndexingOp(ScriptExpression script) {
        this.script = script;
    }

    ScriptExpression script() { return this.script; }

    public boolean doesAttributing() { return containsExpression(AttributeExpression.class); }
    public boolean doesIndexing() { return containsExpression(IndexExpression.class); }
    public boolean doesLowerCasing() { return containsExpression(LowerCaseExpression.class); }
    public boolean doesSummarying() { return containsExpression(SummaryExpression.class); }

    private <T extends Expression> boolean containsExpression(Class<T> searchFor) {
        var searcher = new ExpressionSearcher<>(searchFor);
        var expr = searcher.searchIn(script);
        return (expr != null);
    }
}
