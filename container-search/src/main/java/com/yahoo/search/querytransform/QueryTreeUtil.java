// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.querytransform;

import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.search.Query;
import com.yahoo.search.query.QueryTree;

/**
 * Utility class for manipulating a QueryTree.
 *
 * @author geirst
 */
public class QueryTreeUtil {

    static public void andQueryItemWithRoot(Query query, Item item) {
        andQueryItemWithRoot(query.getModel().getQueryTree(), item);
    }

    static public void andQueryItemWithRoot(QueryTree tree, Item item) {
        if (tree.isEmpty()) {
            tree.setRoot(item);
        } else {
            Item oldRoot = tree.getRoot();
            if (oldRoot.getClass() == AndItem.class) {
                ((AndItem) oldRoot).addItem(item);
            } else {
                AndItem newRoot = new AndItem();
                newRoot.addItem(oldRoot);
                newRoot.addItem(item);
                tree.setRoot(newRoot);
            }
        }
    }

}
