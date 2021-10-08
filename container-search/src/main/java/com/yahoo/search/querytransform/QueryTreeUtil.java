// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.querytransform;

import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.QueryCanonicalizer;
import com.yahoo.search.Query;
import com.yahoo.search.query.QueryTree;

/**
 * Utility class for manipulating a QueryTree.
 *
 * @author geirst
 * @deprecated use QueryTree.and instead // TODO: Remove on Vespa 8
 */
@Deprecated
public class QueryTreeUtil {

    /**
     * Adds the given item to this query
     *
     * @return the new root of the query tree
     */
    static public Item andQueryItemWithRoot(Query query, Item item) {
        return andQueryItemWithRoot(query.getModel().getQueryTree(), item);
    }

    /**
     * Adds the given item to this query
     *
     * @return the new root of the query tree
     */
    static public Item andQueryItemWithRoot(QueryTree tree, Item item) {
        return tree.and(item);
    }

}
