package com.yahoo.search.querytransform;

import com.yahoo.prelude.query.*;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;

public class WeakAndReplacementSearcher extends Searcher {
    private static final CompoundName WEAKAND_REPLACE = new CompoundName("weakand.replace");

    @Override public Result search(Query query, Execution execution) {
        if (!query.properties().getBoolean(WEAKAND_REPLACE)) {
            return execution.search(query);
        }
        replaceOrItems(query);
        return execution.search(query);
    }

    private void replaceOrItems(Query query) {
        Item root = query.getModel().getQueryTree().getRoot();
        int hits = query.properties().getInteger("wand.hits", WeakAndItem.defaultN);
        query.getModel().getQueryTree().setRoot(replaceOrItems(root, hits));
    }

    private Item replaceOrItems(Item item, int hits) {
        if (!(item instanceof CompositeItem)) {
            return item;
        }
        CompositeItem compositeItem = (CompositeItem) item;
        if (item instanceof OrItem) {
            WeakAndItem newItem = new WeakAndItem(hits);
            newItem.setWeight(item.getWeight());
            compositeItem.items().forEach(newItem::addItem);
            compositeItem = newItem;
        }
        for (int i = 0; i < compositeItem.getItemCount(); i++) {
            compositeItem.setItem(i, replaceOrItems(compositeItem.getItem(i), hits));
        }
        return compositeItem;
    }
}
