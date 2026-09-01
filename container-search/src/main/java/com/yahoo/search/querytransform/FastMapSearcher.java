// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.querytransform;

import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.ExactStringItem;
import com.yahoo.prelude.query.IntItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.QueryCanonicalizer;
import com.yahoo.prelude.query.SameElementItem;
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
import com.yahoo.text.Text;

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
        rewriteFastMapSearch(query, session);
        return execution.search(query);
    }

    private void rewriteFastMapSearch(Query query, SchemaInfo.Session session) {
        Item root = query.getModel().getQueryTree().getRoot();
        Item possibleNewRoot = rewriteFastMapSearchVisit(root, session);
        if (root != possibleNewRoot) {
            query.getModel().getQueryTree().setRoot(possibleNewRoot);
            query.trace("rewrote sameElement to fast-map lookup", true, 2);
        }
    }

    /**
     * Rewrite sameElement for fast map search.
     *
     * Package-private for unit testing.
     */
    Item rewriteFastMapSearchVisit(Item item, SchemaInfo.Session session) {
        if (item == null) {
            return null;
        }

        // handle sameelement rewrite
        if (item instanceof SameElementItem sameElementItem) {
            Field.MapFieldType mapType = fastMapSearchType(sameElementItem.getFieldName(), session);
            if (mapType != null) {
                WordItem rewritten = tryMakeFastMapItem(sameElementItem, mapType);
                if (rewritten != null) {
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

    /**
     * Returns the single fast map lookup term equivalent to the given sameElement,
     * or null if the sameElement cannot be expressed as one.
     */
    private WordItem tryMakeFastMapItem(SameElementItem sameElementItem, Field.MapFieldType mapType) {
        if (!sameElementItem.getElementFilter().isEmpty()) {
            return null; // element filter used for arrays.
        }
        if (sameElementItem.getItemCount() != 2) {
            return null;
        }

        Item first = sameElementItem.getItem(0);
        Item second = sameElementItem.getItem(1);
        TermItem key = termWithIndex("key", first, second);
        TermItem value = termWithIndex("value", first, second);
        if (key == null || value == null) {
            return null;
        }

        String encodedKey = encodeExactTerm(key, mapType.keyType().kind());
        String encodedValue = encodeExactTerm(value, mapType.valueType().kind());
        if (encodedKey == null || encodedValue == null) {
            return null;
        }

        return makeFastMapSearchWordItem(encodedKey, encodedValue, sameElementItem.getFieldName());
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

    /**
     * Returns the encoded form of a term which matches exactly one key or value of the
     * given kind, or null if the term cannot be part of a fast map lookup: A subclass of
     * WordItem such as PrefixItem, or an int range, matches more than one exact value.
     */
    private static String encodeExactTerm(TermItem term, Field.Type.Kind kind) {
        if (kind == Field.Type.Kind.STRING) {
            if (term.getClass() == WordItem.class || term instanceof ExactStringItem) {
                return ((WordItem) term).getWord();
            }
            return null;
        }
        if (kind == Field.Type.Kind.INT) {
            String number;
            if (term instanceof IntItem intItem) {
                number = intItem.getNumber();
            } else if (term.getClass() == WordItem.class) {
                number = ((WordItem) term).getWord();
            } else {
                return null;
            }
            try {
                return Text.toExcessHex8(Integer.parseInt(number.trim()));
            } catch (NumberFormatException e) {
                return null; // a range expression, or not an int: fall back to regular sameElement
            }
        }
        return null;
    }

    /** Package-private for unit testing. */
    WordItem makeFastMapSearchWordItem(String encodedKey, String encodedValue, String fieldName) {
        return new WordItem(FastMapSearch.toKeyValueTerm(encodedKey, encodedValue),
                FastMapSearch.toKeyValueFieldName(fieldName), false);
    }

    /** Returns the map type of the given field if it has fast map search enabled, and null otherwise. */
    private static Field.MapFieldType fastMapSearchType(String fieldName, SchemaInfo.Session session) {
        FieldInfo info = session.fieldInfo(fieldName).orElse(null);
        if (info == null || !info.hasFastMapSearch()) {
            return null;
        }
        if (info.type() instanceof Field.MapFieldType mapType) {
            return mapType;
        }
        return null;
    }

}
