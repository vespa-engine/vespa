// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.ScriptExpression;
import com.yahoo.vespa.indexinglanguage.parser.ParseException;

import java.util.Map;

public class ScriptTester {

    public Expression expressionFrom(String string) {
        try {
            return Expression.fromString(string, new SimpleLinguistics(), Map.of());
        }
        catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public ScriptExpression scriptFrom(String string) {
        try {
            return ScriptExpression.fromString(string, new SimpleLinguistics(), Map.of());
        }
        catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

}
