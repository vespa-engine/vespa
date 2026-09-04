// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.querytransform;

import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.ExactStringItem;
import com.yahoo.prelude.query.IntItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.Limit;
import com.yahoo.prelude.query.QueryCanonicalizer;
import com.yahoo.prelude.query.SameElementItem;
import com.yahoo.prelude.query.StringRangeItem;
import com.yahoo.prelude.query.TermItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.schema.Field;
import com.yahoo.search.schema.FieldInfo;
import com.yahoo.search.schema.SchemaInfo;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.PhaseNames;
import com.yahoo.searchlib.document.FastMapSearch;

/**
 * When a field has fast map search enabled, this class transforms queries on
 * that field to target the fast map attribute.
 *
 * @author johsol
 */
@Before(QueryCanonicalizer.queryCanonicalization)
@After(PhaseNames.TRANSFORMED_QUERY)
public class FastMapSearcher extends Searcher {

    @Override
    public Result search(Query query, Execution execution) {
        var schemaInfo = execution.context().schemaInfo();
        var session = schemaInfo.newSession(query);
        new Rewriter().rewriteFastMapSearch(query, session);
        return execution.search(query);
    }

    private class Rewriter {

        private boolean hasRewritten = false;

        /** Entry point for rewriting a query */
        private void rewriteFastMapSearch(Query query, SchemaInfo.Session session) {
            Item root = query.getModel().getQueryTree().getRoot();
            Item possibleNewRoot = rewriteFastMapSearchVisit(root, session);
            if (root != possibleNewRoot) {
                query.getModel().getQueryTree().setRoot(possibleNewRoot);
            }
            if (hasRewritten) {
                query.trace("rewrote sameElement to fast-map lookup", true, 2);
            }
        }

        /**
         * Rewrite sameElement for fast map search.
         */
        private Item rewriteFastMapSearchVisit(Item item, SchemaInfo.Session session) {
            if (item == null) {
                return null;
            }

            // handle sameelement rewrite
            if (item instanceof SameElementItem sameElementItem) {
                Field.MapFieldType mapType = fastMapSearchType(sameElementItem.getFieldName(), session);
                if (mapType != null) {
                    TermItem rewritten = tryMakeFastMapItem(sameElementItem, mapType);
                    if (rewritten != null) {
                        hasRewritten = true;
                        return rewritten;
                    }
                }
            }

            // recursively try rewrite children.
            if (item instanceof CompositeItem composite) {
                for (int i = 0; i < composite.getItemCount(); i++) {
                    Item child = composite.getItem(i);
                    Item newChild = rewriteFastMapSearchVisit(child, session);
                    if (newChild != child) {
                        composite.setItem(i, newChild);
                    }
                }
            }

            return item;
        }

    }

    /**
     * Returns the single fast map lookup term equivalent to the given sameElement,
     * or null if the sameElement cannot be expressed as one. A single value becomes a
     * word lookup, a range becomes a lexical range, both on the synthetic attribute.
     */
    private TermItem tryMakeFastMapItem(SameElementItem sameElementItem, Field.MapFieldType mapType) {
        if (!sameElementItem.getElementFilter().isEmpty()) {
            return null; // element filter used for arrays.
        }

        if (sameElementItem.getItemCount() != 2) {
            return null;
        }

        // resolve which is key and which is value.
        Item first = sameElementItem.getItem(0);
        Item second = sameElementItem.getItem(1);
        TermItem keyItem = termWithIndex("key", first, second);
        TermItem valueItem = termWithIndex("value", first, second);
        if (keyItem == null || valueItem == null) {
            return null;
        }

        // only support string keys for now.
        if (mapType.keyType().kind() != Field.Type.Kind.STRING) {
            return null;
        }

        String fieldName = sameElementItem.getFieldName();

        if (mapType.valueType().kind() == Field.Type.Kind.STRING) {
            var key = getString(keyItem);
            var value = getString(valueItem);
            if (key == null || value == null) {
                return null;
            }
            return makeWord(key, value, fieldName);
        }

        if (mapType.valueType().kind() == Field.Type.Kind.INT) {
            var key = getString(keyItem);
            if (key == null) {
                return null;
            }
            var value = getInteger(valueItem);
            if (value != null) {
                return makeWord(key, value, fieldName);
            }
            return makeIntRange(key, valueItem, fieldName);
        }

        return null;
    }

    /** Returns the first of the two items which is a term with the given index name, or null. */
    private static TermItem termWithIndex(String indexName, Item first, Item second) {
        if (first instanceof TermItem term && indexName.equals(term.getIndexName())) {
            return term;
        }
        if (second instanceof TermItem term && indexName.equals(term.getIndexName())) {
            return term;
        }
        return null;
    }

    /** Gets value as string or null. */
    private String getString(TermItem term) {
        if (term.getClass() == WordItem.class || term instanceof ExactStringItem) {
            return ((WordItem) term).getWord();
        }
        return null;
    }

    /** Gets value as integer or null. */
    private Integer getInteger(TermItem term) {
       String number;
       if (term instanceof IntItem intItem) {
           number = intItem.getNumber();
       } else if (term.getClass() == WordItem.class) {
           number = ((WordItem) term).getWord();
       } else {
           return null;
       }
       try {
           return Integer.parseInt(number.trim());
       } catch (NumberFormatException e) {
           return null; // a range expression, or not an int: the caller tries the range form
       }
    }

    /** Package-private for unit testing. */
    WordItem makeWord(String key, String value, String fieldName) {
        return new WordItem(FastMapSearch.toKeyValueTerm(key, value),
                            FastMapSearch.toKeyValueFieldName(fieldName), false);
    }

    /**
     * Returns the lexical range over the synthetic attribute equivalent to the given numeric
     * range on the value of one map key, or null if it cannot be expressed as one.
     */
    StringRangeItem makeIntRange(String key, TermItem valueItem, String fieldName) {
        if (!(valueItem instanceof IntItem intItem)) {
            return null;
        }
        if (intItem.getHitLimit() != 0) {
            return null; // a hit limit counts entries in the value attribute, not the synthetic one
        }
        Limit fromLimit = intItem.getFromLimit();
        Limit toLimit = intItem.getToLimit();
        Integer from = toIntBound(fromLimit, Integer.MIN_VALUE);
        Integer to = toIntBound(toLimit, Integer.MAX_VALUE);
        if (from == null || to == null) {
            return null;
        }
        return new StringRangeItem(FastMapSearch.toKeyValue8Term(key, from), fromLimit.isInclusive(),
                                   FastMapSearch.toKeyValue8Term(key, to), toLimit.isInclusive(),
                                   FastMapSearch.toKeyValueFieldName(fieldName), false, null);
    }

    /**
     * Returns the int to encode for the given range endpoint, using the given value when the
     * endpoint is unbounded, or null if the endpoint has no exact int form.
     */
    private static Integer toIntBound(Limit limit, int whenInfinite) {
        if (limit.isInfinite()) {
            return whenInfinite;
        }
        int asInt = limit.number().intValue();
        double asDouble = limit.number().doubleValue();
        if (asDouble != (double)asInt) {
            return null; // not an int endpoint: fall back to the regular sameElement
        }
        return asInt;
    }

    /** Package-private for unit testing. */
    WordItem makeWord(String key, Integer value, String fieldName) {
        return new WordItem(FastMapSearch.toKeyValue8Term(key, value),
                            FastMapSearch.toKeyValueFieldName(fieldName), false);
    }

    /** Returns the map type of the given field if it has fast map search enabled, and null otherwise. */
    private static Field.MapFieldType fastMapSearchType(String fieldName, SchemaInfo.Session session) {
        FieldInfo info = session.fieldInfo(fieldName).orElse(null);
        if (info != null && info.hasFastMapSearch() && info.type() instanceof Field.MapFieldType mapType) {
            return mapType;
        }
        return null;
    }

}
