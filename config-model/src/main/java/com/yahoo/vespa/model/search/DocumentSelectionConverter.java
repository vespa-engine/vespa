// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.document.select.*;
import com.yahoo.document.select.convert.SelectionExpressionConverter;
import com.yahoo.document.select.parser.ParseException;
import java.util.Map;


/**
 * @author <a href="mailto:lulf@yahoo-inc.com">Ulf Lilleengen</a>
 */
// TODO: Public methods only used in tests. What is this class for?
public class DocumentSelectionConverter {

    private final DocumentSelector selector;
    private final Map<String, String> queryExpressionMap;

    public DocumentSelectionConverter(String selection) throws ParseException, UnsupportedOperationException, IllegalArgumentException {
        this.selector = new DocumentSelector(selection);
        NowCheckVisitor nowChecker = new NowCheckVisitor();
        selector.visit(nowChecker);
        if (nowChecker.requiresConversion()) {
            SelectionExpressionConverter converter = new SelectionExpressionConverter();
            selector.visit(converter);
            this.queryExpressionMap = converter.getQueryMap();
        } else {
            this.queryExpressionMap = null;
        }
    }

    /**
     * Transforms the selection into a search query.
     * @return A search query representing the selection.
     */
    public String getQuery(String documentType) {
        if (queryExpressionMap == null)
            return null;
        if (!queryExpressionMap.containsKey(documentType))
            return null;
        return queryExpressionMap.get(documentType);
    }

    /**
     * Transforms the selection into an inverted search query.
     * @return A search query representing the selection.
     */
    public String getInvertedQuery(String documentType) {
        String query = getQuery(documentType);
        if (query == null)
            return null;
        return query.replaceAll(">", "<");
    }

}
