// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.querytransform;

import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.EquivItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.NearItem;
import com.yahoo.prelude.query.NonReducibleCompositeItem;
import com.yahoo.prelude.query.NotItem;
import com.yahoo.prelude.query.NullItem;
import com.yahoo.prelude.query.OrItem;
import com.yahoo.prelude.query.RankItem;
import com.yahoo.prelude.query.SimpleIndexedItem;
import com.yahoo.prelude.query.SubstringItem;
import com.yahoo.search.Query;
import com.yahoo.search.query.Model;
import com.yahoo.search.result.Hit;

/**
 * @author baldersheim
 */
// TODO: This overlaps with QueryCanonicalizer
public class QueryRewrite {

    private enum Recall { RECALLS_EVERYTHING, RECALLS_NOTHING, UNKNOWN_RECALL }

    // ------------------- Start public API
    
    /**
     * Optimize multiple NotItems under and or by collapsing them in to one and leaving
     * the positive ones behind in its place and moving itself with the original and as its positive item
     * and the union of all the negative items of all the original NotItems as its negative items.
     */
    public static void optimizeAndNot(Query query) {
        Item root = query.getModel().getQueryTree().getRoot();
        Item possibleNewRoot = optimizeAndNot(root);
        if (root != possibleNewRoot) {
            query.getModel().getQueryTree().setRoot(possibleNewRoot);
        }
    }

    /**
     * Optimizes the given query tree based on its {@link Model#getRestrict()} parameter, if any.
     */
    public static void optimizeByRestrict(Query query) {
        if (query.getModel().getRestrict().size() != 1) {
            return;
        }
        Item root = query.getModel().getQueryTree().getRoot();
        if (optimizeByRestrict(root, query.getModel().getRestrict().iterator().next()) == Recall.RECALLS_NOTHING) {
            query.getModel().getQueryTree().setRoot(new NullItem());
        }
    }

    /**
     * Collapses all single-child {@link CompositeItem}s into their parent item.
     */
    public static void collapseSingleComposites(Query query) {
        Item oldRoot = query.getModel().getQueryTree().getRoot();
        Item newRoot = collapseSingleComposites(oldRoot);
        if (oldRoot != newRoot) {
            query.getModel().getQueryTree().setRoot(newRoot);
        }
    }

    /**
     * Replaces and {@link SimpleIndexedItem} searching in the {@link Hit#SDDOCNAME_FIELD} with an item
     * appropriate for the search node.
     */
    public static void rewriteSddocname(Query query) {
        Item oldRoot = query.getModel().getQueryTree().getRoot();
        Item newRoot = rewriteSddocname(oldRoot);
        if (oldRoot != newRoot) {
            query.getModel().getQueryTree().setRoot(newRoot);
        }
    }

    // ------------------- End public API

    private static Item optimizeAndNot(Item node) {
        if (node instanceof CompositeItem) {
            return extractAndNotRecursively((CompositeItem) node);
        }
        return node;
    }

    private static CompositeItem extractAndNotRecursively(CompositeItem parent) {
        for (int i = 0; i < parent.getItemCount(); i++) {
            Item child = parent.getItem(i);
            Item possibleNewChild = optimizeAndNot(child);
            if (child != possibleNewChild) {
                parent.setItem(i, possibleNewChild);
            }
        }
        if (parent instanceof AndItem) {
            return extractAndNot((AndItem) parent);
        }
        return parent;
    }

    private static CompositeItem extractAndNot(AndItem parent) {
        NotItem theOnlyNot = null;
        for (int i = 0; i < parent.getItemCount(); i++) {
            Item child = parent.getItem(i);
            if (child instanceof NotItem) {
                NotItem thisNot = (NotItem) child;
                parent.setItem(i, thisNot.getPositiveItem());
                if (theOnlyNot == null) {
                    theOnlyNot = thisNot;
                    theOnlyNot.setPositiveItem(parent);
                } else {
                    for (int j=1; j < thisNot.getItemCount(); j++) {
                        theOnlyNot.addNegativeItem(thisNot.getItem(j));
                    }
                }
            }
        }
        return (theOnlyNot != null) ? theOnlyNot : parent;
    }

    private static Recall optimizeByRestrict(Item item, String restrictParam) {
        if (item instanceof SimpleIndexedItem) {
            return optimizeIndexedItemByRestrict((SimpleIndexedItem)item, restrictParam);
        } else if (item instanceof NotItem) {
            return optimizeNotItemByRestrict((NotItem)item, restrictParam);
        } else if (item instanceof CompositeItem) {
            return optimizeCompositeItemByRestrict((CompositeItem)item, restrictParam);
        } else {
            return Recall.UNKNOWN_RECALL;
        }
    }

    private static Recall optimizeIndexedItemByRestrict(SimpleIndexedItem item, String restrictParam) {
        if (!Hit.SDDOCNAME_FIELD.equals(item.getIndexName())) {
            return Recall.UNKNOWN_RECALL;
        }
        // a query term searching for sddocname will either recall everything or nothing, depending on whether
        // the term matches the restrict parameter or not
        return restrictParam.equals(item.getIndexedString())
               ? Recall.RECALLS_EVERYTHING
               : Recall.RECALLS_NOTHING;
    }

    private static Recall optimizeNotItemByRestrict(NotItem item, String restrictParam) {
        // first item is the positive one
        if (optimizeByRestrict(item.getItem(0), restrictParam) == Recall.RECALLS_NOTHING) {
            return Recall.RECALLS_NOTHING;
        }
        // all the remaining items are negative ones
        for (int i = item.getItemCount(); --i >= 1; ) {
            Item child = item.getItem(i);
            switch (optimizeByRestrict(child, restrictParam)) {
                case RECALLS_EVERYTHING:
                    return Recall.RECALLS_NOTHING;
                case RECALLS_NOTHING:
                    item.removeItem(i);
                    break;
            }
        }
        return Recall.UNKNOWN_RECALL;
    }

    private static Recall optimizeCompositeItemByRestrict(CompositeItem item, String restrictParam) {
        Recall recall = Recall.UNKNOWN_RECALL;
        for (int i = item.getItemCount(); --i >= 0; ) {
            switch (optimizeByRestrict(item.getItem(i), restrictParam)) {
                case RECALLS_EVERYTHING:
                    if ((item instanceof OrItem) || (item instanceof EquivItem)) {
                        removeOtherNonrankedChildren(item, i);
                        recall = Recall.RECALLS_EVERYTHING;
                    } else if ((item instanceof AndItem) || (item instanceof NearItem)) {
                        item.removeItem(i);
                    } else if (item instanceof RankItem) {
                        // empty
                    } else {
                        throw new UnsupportedOperationException(item.getClass().getName());
                    }
                    break;
                case RECALLS_NOTHING:
                    if ((item instanceof OrItem) || (item instanceof EquivItem)) {
                        item.removeItem(i);
                    } else if ((item instanceof AndItem) || (item instanceof NearItem)) {
                        return Recall.RECALLS_NOTHING;
                    } else if (item instanceof RankItem) {
                        item.removeItem(i);
                    } else {
                        throw new UnsupportedOperationException(item.getClass().getName());
                    }
                    break;
            }
        }
        return recall;
    }

    private static void removeOtherNonrankedChildren(CompositeItem parent, int indexOfChildToKeep) {
        Item childToKeep = parent.getItem(indexOfChildToKeep);
        for (int i = parent.getItemCount(); --i >= 0; ) {
            Item child = parent.getItem(i);
            if ( child != childToKeep && ! parent.getItem(i).isRanked())
                parent.removeItem(i);
        }
    }
    
    private static Item collapseSingleComposites(Item item) {
        if (!(item instanceof CompositeItem)) {
            return item;
        }
        CompositeItem parent = (CompositeItem)item;
        int numChildren = parent.getItemCount();
        for (int i = 0; i < numChildren; ++i) {
            Item oldChild = parent.getItem(i);
            Item newChild = collapseSingleComposites(oldChild);
            if (oldChild != newChild) {
                parent.setItem(i, newChild);
            }
        }
        return ((numChildren == 1) && !(parent instanceof NonReducibleCompositeItem)) ? parent.getItem(0) : item;
    }

    private static Item rewriteSddocname(Item item) {
        if (item instanceof CompositeItem) {
            CompositeItem parent = (CompositeItem)item;
            for (int i = 0, len = parent.getItemCount(); i < len; ++i) {
                Item oldChild = parent.getItem(i);
                Item newChild = rewriteSddocname(oldChild);
                if (oldChild != newChild) {
                    parent.setItem(i, newChild);
                }
            }
        } else if (item instanceof SimpleIndexedItem) {
            SimpleIndexedItem oldItem = (SimpleIndexedItem)item;
            if (Hit.SDDOCNAME_FIELD.equals(oldItem.getIndexName())) {
                SubstringItem newItem = new SubstringItem(oldItem.getIndexedString());
                newItem.setIndexName("[documentmetastore]");
                return newItem;
            }
        }
        return item;
    }

}
