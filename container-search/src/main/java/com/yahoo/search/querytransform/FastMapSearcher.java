// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.querytransform;

import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.QueryCanonicalizer;
import com.yahoo.prelude.query.SameElementItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
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
            String fieldName = sameElementItem.getIndexName();
            if (isFastMapSearch(fieldName, session)) {
                var kv = KeyValue.parse(sameElementItem);
                if (kv.hasKeyValue) {
                    return makeFastMapSearchWordItem(kv.key, kv.value, fieldName);
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
     * Parses key and value to string form.
     */
    private record KeyValue(boolean hasKeyValue, String key, String value) {

        /**
         * Recognizes that same element has two elements called key and value, and
         * that they are string items.
         * TODO: extend to handle IntItem
         */
        static KeyValue parse(SameElementItem sameElementItem) {
            if (sameElementItem.getItemCount() == 2) {
                if (sameElementItem.getItem(0) instanceof WordItem item1
                    && sameElementItem.getItem(1) instanceof WordItem item2) {
                    if (isKeyValue(item1, item2)) {
                        return new KeyValue(true, item1.getWord(), item2.getWord());
                    } else if (isKeyValue(item2, item1)) {
                        return new KeyValue(true,  item2.getWord(), item1.getWord());
                    }
                }
            }

            return new KeyValue(false, null, null);
        }

        static boolean isKeyValue(WordItem key, WordItem value) {
            return "key".equals(key.getIndexName()) && "value".equals(value.getIndexName());
        }
    }

    /** Package-private for unit testing. */
    WordItem makeFastMapSearchWordItem(String key, String value, String fieldName) {
        return new WordItem(key + FastMapSearch.keyValueSeparator() + value,
                FastMapSearch.toKeyValueFieldName(fieldName), false);
    }

    /** Returns whether the given field has fast map search enabled. */
    private boolean isFastMapSearch(String fieldName, SchemaInfo.Session session) {
        return session.fieldInfo(fieldName).map(FieldInfo::hasFastMapSearch).orElse(false);
    }

}
