// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.querytransform;

import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Provides;
import com.yahoo.prelude.query.PredicateQueryItem;
import com.yahoo.processing.IllegalInputException;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.grouping.request.parser.TokenMgrException;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.searchchain.Execution;

import java.math.BigInteger;

import static com.yahoo.prelude.querytransform.NormalizingSearcher.ACCENT_REMOVAL;
import static com.yahoo.prelude.querytransform.StemmingSearcher.STEMMING;
import static com.yahoo.yolean.Exceptions.toMessageString;

/**
 * Searcher that builds a PredicateItem from the &amp;boolean properties and inserts it into a query.
 *
 * @author Magnar Nedland
 */
@After({ STEMMING, ACCENT_REMOVAL })
@Provides(BooleanSearcher.PREDICATE)
public class BooleanSearcher extends Searcher {

    private static final CompoundName FIELD = new CompoundName("boolean.field");
    private static final CompoundName ATTRIBUTES = new CompoundName("boolean.attributes");
    private static final CompoundName RANGE_ATTRIBUTES = new CompoundName("boolean.rangeAttributes");
    public static final String PREDICATE = "predicate";

    @Override
    public Result search(Query query, Execution execution) {
        String fieldName = query.properties().getString(FIELD);
        if (fieldName != null) {
            return search(query, execution, fieldName);
        } else {
            if (query.getTrace().isTraceable(5)) {
                query.trace("BooleanSearcher: Nothing added to query", false, 5);
            }
        }
        return execution.search(query);
    }

    private Result search(Query query, Execution execution, String fieldName) {
        String attributes = query.properties().getString(ATTRIBUTES);
        String rangeAttributes = query.properties().getString(RANGE_ATTRIBUTES);
        if (query.getTrace().isTraceable(5)) {
            query.trace("BooleanSearcher: fieldName(" + fieldName + "), attributes(" + attributes +
                        "), rangeAttributes(" + rangeAttributes + ")", false, 5);
        }

        if (attributes != null || rangeAttributes != null) {
            try {
                addPredicateTerm(query, fieldName, attributes, rangeAttributes);
                if (query.getTrace().isTraceable(4)) {
                    query.trace("BooleanSearcher: Added boolean operator", true, 4);
                }
            } catch (TokenMgrException e) {
                return new Result(query, ErrorMessage.createInvalidQueryParameter(toMessageString(e)));
            }
            catch (IllegalArgumentException e) {
                throw new IllegalInputException("Failed boolean search on field '" + fieldName + "'", e);
            }
        }
        else {
            if (query.getTrace().isTraceable(5)) {
                query.trace("BooleanSearcher: Nothing added to query", false, 5);
            }
        }
        return execution.search(query);
    }

     // Adds a boolean term ANDed to the query, based on the supplied properties.
    private void addPredicateTerm(Query query, String fieldName, String attributes, String rangeAttributes) {
        PredicateQueryItem item = new PredicateQueryItem();
        item.setIndexName(fieldName);
        new PredicateValueAttributeParser(item).parse(attributes);
        new PredicateRangeAttributeParser(item).parse(rangeAttributes);
        query.getModel().getQueryTree().and(item);
    }

    static public class PredicateValueAttributeParser extends BooleanAttributeParser {

        private final PredicateQueryItem item;

        public PredicateValueAttributeParser(PredicateQueryItem item) {
            this.item = item;
        }

        @Override
        protected void addAttribute(String attribute, String value) {
            item.addFeature(attribute, value);
        }

        @Override
        protected void addAttribute(String attribute, String value, BigInteger subQueryMask) {
            item.addFeature(attribute, value, subQueryMask.longValue());
        }

    }

    static private class PredicateRangeAttributeParser extends BooleanAttributeParser {

        private final PredicateQueryItem item;

        public PredicateRangeAttributeParser(PredicateQueryItem item) {
            this.item = item;
        }

        @Override
        protected void addAttribute(String attribute, String value) {
            item.addRangeFeature(attribute, Long.parseLong(value));
        }

        @Override
        protected void addAttribute(String attribute, String value, BigInteger subQueryMask) {
            item.addRangeFeature(attribute, Long.parseLong(value), subQueryMask.longValue());
        }

    }

}
