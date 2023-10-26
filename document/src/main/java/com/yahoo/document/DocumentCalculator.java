// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.document.select.Context;
import com.yahoo.document.select.parser.ParseException;
import com.yahoo.document.select.parser.SelectInput;
import com.yahoo.document.select.parser.SelectParser;
import com.yahoo.document.select.rule.ComparisonNode;

import java.util.Map;

/**
 * @author Thomas Gundersen
 */
public class DocumentCalculator {

    private ComparisonNode comparison;

    public DocumentCalculator(String expression) throws ParseException {
        SelectParser parser = new SelectParser(new SelectInput(expression + " == 0"));
        comparison = (ComparisonNode)parser.expression();
    }

    public Number evaluate(Document doc, Map<String, Object> variables) {
        Context context = new Context(new DocumentPut(doc));
        context.setVariables(variables);

        try {
            Object o = comparison.getLHS().evaluate(context);

            if (Double.isInfinite(((Number)o).doubleValue())) {
                throw new IllegalArgumentException("Expression evaluated to an infinite number");
            }
            return ((Number)o).doubleValue();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Arithmetic exception " + e.getMessage(), e);
        }
    }

}
