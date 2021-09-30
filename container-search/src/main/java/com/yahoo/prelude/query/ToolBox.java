// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import com.google.common.annotations.Beta;

/**
 * Query tree helper methods and factories.
 *
 * @author Steinar Knutsen
 */
@Beta
public final class ToolBox {

    public static abstract class QueryVisitor {

        /**
         * Called for each item in the query tree given to
         * {@link ToolBox#visit(QueryVisitor, Item)}. Return true to visit the
         * sub-items of the given item, return false to ignore the sub-items.
         *
         * @param item each item in the query tree
         * @return whether or not to visit the sub-items of the argument item
         *         (and then invoke the {@link #onExit()} method)
         */
        public abstract boolean visit(Item item);

        /**
         * Invoked when all sub-items have been visited, or immediately after
         * visit() if there are no sub-items or visit() returned false.
         */
        public abstract void onExit();

    }

    public static void visit(QueryVisitor visitor, Item item) {
        if (item instanceof CompositeItem) {
            if (visitor.visit(item)) {
                CompositeItem composite = (CompositeItem) item;
                for (int i = 0; i < composite.getItemCount(); ++i) {
                    visit(visitor, composite.getItem(i));
                }
            }
        } else {
            visitor.visit(item);
        }
        visitor.onExit();
    }

}
