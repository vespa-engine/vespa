// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.search.searchers;

import com.yahoo.api.annotations.Beta;
import com.yahoo.component.chain.dependencies.After;
import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.WeakAndItem;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.querytransform.WeakAndReplacementSearcher;
import com.yahoo.search.searchchain.Execution;

/**
 * Will opportunistically replace the WeakAND with an AND as it is faster.
 * If enough hits are returned all is good and we return. If not we fall back to the original query.
 * It is default off, and is enabled with weakAnd.opportunistic.and=true.
 * It can be tuned with weakAnd.opportunistic.factor. Higher value than 1 might increase quality, lower value will
 * improve performance. Default is 1.0. This factor is multiplied with the heap size of the wand(default 100) as target hits.
 *
 * @author baldersheim
 */
@Beta
@After(WeakAndReplacementSearcher.REPLACE_OR_WITH_WEAKAND)
public class OpportunisticWeakAndSearcher extends Searcher {
    private static final CompoundName OPPORTUNISTIC_AND = CompoundName.from("weakAnd.opportunistic.and");
    private static final CompoundName OPPORTUNISTIC_FACTOR = CompoundName.from("weakAnd.opportunistic.factor");

    @Override
    public Result search(Query query, Execution execution) {
        if (!query.properties().getBoolean(OPPORTUNISTIC_AND)) {
            return execution.search(query);
        }

        Item originalRoot = query.getModel().getQueryTree().getRoot();
        int targetHits = (int)(targetHits(originalRoot) * query.properties().getDouble(OPPORTUNISTIC_FACTOR, 1.0));
        if (targetHits >= 0) {
            query.getModel().getQueryTree().setRoot(weakAnd2AndRecurse(originalRoot.clone()));
            query.trace("WeakAND(" + targetHits+ ") => AND", true, 2);
            Result result = execution.search(query);
            if (result.getTotalHitCount() >= targetHits) {
                return result;
            }
            query.getModel().getQueryTree().setRoot(originalRoot);
            query.trace("Fallback to WeakAND(" + targetHits+ ") as AND => " + result, true, 2);
            return execution.search(query);
        }
        return execution.search(query);
    }

    // returns targetHits for the first WeakAndItem found, -1 if none found.
    static int targetHits(Item item) {
        if (!(item instanceof CompositeItem compositeItem)) return -1;
        if (item instanceof WeakAndItem weakAndItem) {
            return (weakAndItem.getItemCount() >= 2) ? weakAndItem.getN() : -1;
        }
        for (int i = 0; i < compositeItem.getItemCount(); i++) {
            int targetHits = targetHits(compositeItem.getItem(i));
            if (targetHits >= 0) return targetHits;
        }
        return -1;
    }

    static Item weakAnd2AndRecurse(Item item) {
        if (!(item instanceof CompositeItem compositeItem)) return item;
        compositeItem = weakAnd2And(compositeItem);
        for (int i = 0; i < compositeItem.getItemCount(); i++) {
            Item subItem = compositeItem.getItem(i);
            Item replacedItem = weakAnd2AndRecurse(subItem);
            if (replacedItem != subItem) {
                compositeItem.setItem(i, replacedItem);
            }
        }
        return compositeItem;
    }

    private static CompositeItem weakAnd2And(CompositeItem item) {
        if (item instanceof WeakAndItem weakAndItem) {
            AndItem andItem = new AndItem();
            andItem.setWeight(weakAndItem.getWeight());
            item.items().forEach(andItem::addItem);
            return andItem;
        }
        return item;
    }
}
