// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.select;

import com.yahoo.document.DocumentOperation;
import com.yahoo.document.select.parser.ParseException;
import com.yahoo.document.select.parser.SelectInput;
import com.yahoo.document.select.parser.SelectParser;
import com.yahoo.document.select.parser.TokenMgrError;
import com.yahoo.document.select.rule.ExpressionNode;

/**
 * <p>A document selector is a filter which accepts or rejects documents
 * based on their type and content. A document selector has a textual
 * representation which is called the <i>Document Selection Language</i></p>
 *
 * <p>Document selectors are multithread safe.</p>
 *
 * @author bratseth
 */
public class DocumentSelector {

    private ExpressionNode expression;

    /**
     * Creates a document selector from a Document Selection Language string
     *
     * @param selector The string to parse as a selector.
     * @throws ParseException Thrown if the string could not be parsed.
     */
    public DocumentSelector(String selector) throws ParseException {
        SelectInput input = new SelectInput(selector);
        try {
            SelectParser parser = new SelectParser(input);
            expression = parser.expression();
        } catch (TokenMgrError e) {
            ParseException t = new ParseException("Tokenization error parsing document selector '" + selector + "'");
            throw (ParseException)t.initCause(e);
        } catch (RuntimeException | ParseException e) {
            ParseException t = new ParseException("Exception parsing document selector '" + selector + "'");
            throw (ParseException)t.initCause(e instanceof ParseException ?
                                              new ParseException(input.formatException(e.getMessage())) : e);
        }
    }

    /**
     * Returns true if the document referenced by this document operation is accepted by this selector
     *
     * @param op A document operation
     * @return True if the document is accepted.
     * @throws RuntimeException if the evaluation enters an illegal state
     */
    public Result accepts(DocumentOperation op) {
        return accepts(new Context(op));
    }

    /**
     * Returns true if the document referenced by this context is accepted by this selector
     *
     * @param context The context to match in.
     * @return True if the document is accepted.
     * @throws RuntimeException if the evaluation enters an illegal state
     */
    public Result accepts(Context context) {
        return Result.toResult(expression.evaluate(context));
    }

    /**
     * Returns the list of different variables resulting in a true state for this
     * expression.
     *
     * @param op The document to evaluate.
     * @return True if the document is accepted.
     * @throws RuntimeException if the evaluation enters an illegal state
     */
    public ResultList getMatchingResultList(DocumentOperation op) {
        return getMatchingResultList(new Context(op));
    }

    /**
     * Returns the list of different variables resulting in a true state for this
     * expression.
     *
     * @param context The context to match in.
     * @return True if the document is accepted.
     * @throws RuntimeException if the evaluation enters an illegal state
     */
    public ResultList getMatchingResultList(Context context) {
        return ResultList.toResultList(expression.evaluate(context));
    }

    /**
     * Returns this selector as a Document Selection Language string.
     *
     * @return The selection string.
     */
    public String toString() {
        return expression.toString();
    }

    /**
     *  Returns the ordering specification, if any, implied by this document
     *  selection expression.
     *
     *  @param order The order of the
     */
    public OrderingSpecification getOrdering(int order) {
        return expression.getOrdering(order);
    }

    /**
     * Visits the expression tree.
     *
     * @param visitor The visitor to use.
     */
    public void visit(Visitor visitor) {
        expression.accept(visitor);
    }
}
