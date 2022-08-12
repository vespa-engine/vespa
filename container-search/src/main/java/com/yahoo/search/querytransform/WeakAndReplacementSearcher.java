// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.querytransform;

import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.OrItem;
import com.yahoo.prelude.query.WeakAndItem;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;

/**
 * Recursively replaces all instances of OrItems with WeakAndItems if the query property weakand.replace is true.
 * Otherwise a noop searcher.
 *
 * @author karowan
 */
public class WeakAndReplacementSearcher extends Searcher {
    static final CompoundName WEAKAND_REPLACE = new CompoundName("weakAnd.replace");
    static final CompoundName WAND_HITS = new CompoundName("wand.hits");

    @Override public Result search(Query query, Execution execution) {
        if (!query.properties().getBoolean(WEAKAND_REPLACE)) {
            return execution.search(query);
        }
        replaceOrItems(query);
        return execution.search(query);
    }

    /**
     * Extracts the queryTree root and the wand.hits property to send to the recursive replacement function
     * @param query the search query
     */
    private void replaceOrItems(Query query) {
        Item root = query.getModel().getQueryTree().getRoot();
        int hits = query.properties().getInteger(WAND_HITS, WeakAndItem.defaultN);
        query.getModel().getQueryTree().setRoot(replaceOrItems(root, hits));
        if (root != query.getModel().getQueryTree().getRoot())
            query.trace("Replaced OR by WeakAnd", true, 2);
    }


    /**
     * Recursively iterates over an Item to replace all instances of OrItems with WeakAndItems
     * @param item the current item in the replacement iteration
     * @param hits the wand.hits property from the request which is assigned to the N value of the new WeakAndItem
     * @return the original item or a WeakAndItem replacement of an OrItem
     */
    private Item replaceOrItems(Item item, int hits) {
        if (!(item instanceof CompositeItem compositeItem)) {
            return item;
        }
        if (compositeItem instanceof OrItem) {
            WeakAndItem newItem = new WeakAndItem(hits);
            newItem.setWeight(compositeItem.getWeight());
            compositeItem.items().forEach(newItem::addItem);
            compositeItem = newItem;
        }
        for (int i = 0; i < compositeItem.getItemCount(); i++) {
            Item subItem = compositeItem.getItem(i);
            Item replacedItem = replaceOrItems(subItem, hits);
            if (replacedItem != subItem) {
                compositeItem.setItem(i, replacedItem);
            }
        }
        return compositeItem;
    }

}
