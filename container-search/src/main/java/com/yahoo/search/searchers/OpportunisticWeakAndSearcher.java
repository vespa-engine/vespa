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
 * Opportunistically replace the WeakAND with an AND as it is faster.
 * If enough hits are returned all is good and we return. If not we fall back to the original query.
 * This is off by default, and is enabled with weakAnd.opportunistic.and=true.
 * This can be tuned with weakAnd.opportunistic.factor. Higher value than 1 might increase quality, lower value will
 * improve performance. Default is 1.0. This factor is multiplied with the heap size of the wand(default 100) as target hits.
 *
 * @author baldersheim
 */
@Beta
@After(WeakAndReplacementSearcher.REPLACE_OR_WITH_WEAKAND)
public class OpportunisticWeakAndSearcher extends Searcher {

    private static final CompoundName OPPORTUNISTIC_AND = CompoundName.from("weakAnd.opportunistic.and");
    private static final CompoundName OPPORTUNISTIC_FACTOR = CompoundName.from("weakAnd.opportunistic.factor");

    private static final int minimalTargetHits = 100;

    @Override
    public Result search(Query query, Execution execution) {
        if (query.getHits() > minimalTargetHits) {
            adjustWeakAndHeap(query.getModel().getQueryTree().getRoot(), query.getHits());
        }
        if (!query.properties().getBoolean(OPPORTUNISTIC_AND)) {
            return execution.search(query);
        }

        Item originalRoot = query.getModel().getQueryTree().getRoot();
        Integer targetHits = targetHits(originalRoot);
        if (targetHits != null) {
            int scaledTargetHits = (int)(targetHits * query.properties().getDouble(OPPORTUNISTIC_FACTOR, 1.0));
            query.getModel().getQueryTree().setRoot(weakAnd2AndRecurse(originalRoot.clone()));
            if (query.getTrace().getLevel() >= 2)
                query.trace("WeakAND(" + scaledTargetHits + ") => AND", true, 2);
            Result result = execution.search(query);
            if (result.getTotalHitCount() >= scaledTargetHits) {
                return result;
            }
            query.getModel().getQueryTree().setRoot(originalRoot);
            query.trace("Fallback to WeakAND(" + scaledTargetHits + ") as AND => " + result, true, 2);
            return execution.search(query);
        }
        return execution.search(query);
    }

    static Item adjustWeakAndHeap(Item item, int hits) {
        if (item instanceof WeakAndItem weakAnd && weakAnd.getTargetHits() == null && hits > minimalTargetHits) {
            weakAnd.setTargetHits(hits);
        }
        if (item instanceof CompositeItem compositeItem) {
            for (int i = 0; i < compositeItem.getItemCount(); i++) {
                adjustWeakAndHeap(compositeItem.getItem(i), hits);
            }
        }
        return item;
    }

    /** Returns targetHits of the first WeakAndItem found. */
    static Integer targetHits(Item item) {
        if (!(item instanceof CompositeItem compositeItem)) return null;
        if (item instanceof WeakAndItem weakAndItem) {
            return (weakAndItem.getItemCount() >= 2) ? weakAndItem.getTargetHits() : null;
        }
        for (int i = 0; i < compositeItem.getItemCount(); i++) {
            Integer targetHits = targetHits(compositeItem.getItem(i));
            if (targetHits != null) return targetHits;
        }
        return null;
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
