// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.select;

import com.yahoo.document.DocumentOperation;
import com.yahoo.document.select.parser.ParseException;
import com.yahoo.document.select.parser.SelectInput;
import com.yahoo.document.select.parser.SelectParser;
import com.yahoo.document.select.parser.TokenMgrException;
import com.yahoo.document.select.rule.ExpressionNode;

/**
 * A document selector is a filter which accepts or rejects documents
 * based on their type and content. A document selector has a textual
 * representation which is called the
 * <a href="https://docs.vespa.ai/en/reference/document-select-language.html">document selection language</a>.
 *
 * Document selectors are multithread safe.
 *
 * @author bratseth
 */
public class DocumentSelector {

    private final ExpressionNode expression;

    /**
     * Creates a document selector from a Document Selection Language string
     *
     * @param selector the string to parse as a selector
     * @throws ParseException Thrown if the string could not be parsed
     */
    public DocumentSelector(String selector) throws ParseException {
        SelectInput input = new SelectInput(selector);
        try {
            SelectParser parser = new SelectParser(input);
            expression = parser.expression();
        } catch (TokenMgrException e) {
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
     * @param op a document operation
     * @return true if the document is accepted
     * @throws RuntimeException if the evaluation enters an illegal state
     */
    public Result accepts(DocumentOperation op) {
        return accepts(new Context(op));
    }

    /**
     * Returns true if the document referenced by this context is accepted by this selector
     *
     * @param context the context to match in
     * @return true if the document is accepted
     * @throws RuntimeException if the evaluation enters an illegal state
     */
    public Result accepts(Context context) {
        return Result.toResult(expression.evaluate(context));
    }

    /**
     * Returns the list of different variables resulting in a true state for this expression
     *
     * @param op the document to evaluate
     * @return true if the document is accepted
     * @throws RuntimeException if the evaluation enters an illegal state
     */
    public ResultList getMatchingResultList(DocumentOperation op) {
        return getMatchingResultList(new Context(op));
    }

    /**
     * Returns the list of different variables resulting in a true state for this expression
     *
     * @param context the context to match in
     * @return true if the document is accepted
     * @throws RuntimeException if the evaluation enters an illegal state
     */
    private ResultList getMatchingResultList(Context context) {
        return ResultList.toResultList(expression.evaluate(context));
    }

    /** Returns this selector as a Document Selection Language string */
    @Override
    public String toString() {
        return expression.toString();
    }

    /** Visits the expression tree */
    public void visit(Visitor visitor) {
        expression.accept(visitor);
    }

}
