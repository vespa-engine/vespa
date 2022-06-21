// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.querytransform;

import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.prelude.Index;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.NotItem;
import com.yahoo.prelude.query.RankItem;
import com.yahoo.prelude.query.TermItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.PhaseNames;

import java.util.Iterator;

import static com.yahoo.prelude.querytransform.StemmingSearcher.STEMMING;
import static com.yahoo.language.LinguisticsCase.toLowerCase;

/**
 * Adds rank terms to boost hits matching exact literals fields using info
 * from indexing commands.
 *
 * @author bratseth
 */
@Before(STEMMING)
@After(PhaseNames.UNBLENDED_RESULT)
public class LiteralBoostSearcher extends Searcher {

    @Override
    public Result search(Query query, Execution execution) {
        addRankTerms(query, execution.context().getIndexFacts().newSession(query));
        return execution.search(query);
    }

    private void addRankTerms(Query query, IndexFacts.Session indexFacts) {
        RankItem newRankTerms = new RankItem();
        addLiterals(newRankTerms, query.getModel().getQueryTree().getRoot(), indexFacts);
        if (newRankTerms.getItemCount() > 0)
            addTopLevelRankTerms(newRankTerms, query);

        if (query.getTrace().getLevel() >= 2 && newRankTerms.getItemCount() > 0)
            query.trace("Added rank terms for possible literal field matches.", true, 2);
    }

    /**
     * Adds a RankItem at the root of a query, but only if there is
     * at least one rank term in the specified RankItem.
     * If the root is already a RankItem, just append the new rank terms.
     *
     * @param rankTerms the new rank item to add.
     * @param query the query to add to
     */
    private void addTopLevelRankTerms(RankItem rankTerms, Query query) {
        Item root = query.getModel().getQueryTree().getRoot();
        if (root instanceof RankItem) {
            for (Iterator<Item> i = rankTerms.getItemIterator(); i.hasNext(); ) {
                ((RankItem)root).addItem(i.next());
            }
        }
        else {
            rankTerms.addItem(0, root);
            query.getModel().getQueryTree().setRoot(rankTerms);

        }
    }

    private void addLiterals(RankItem rankTerms, Item item, IndexFacts.Session indexFacts) {
        if (item instanceof NotItem) {
            addLiterals(rankTerms, ((NotItem) item).getPositiveItem(), indexFacts);
        }
        else if (item instanceof CompositeItem) {
            for (Iterator<Item> i = ((CompositeItem)item).getItemIterator(); i.hasNext(); )
                addLiterals(rankTerms, i.next(), indexFacts);
        }
        else if (item instanceof TermItem) {
            TermItem termItem = (TermItem)item;
            Index index = indexFacts.getIndex(termItem.getIndexName());
            if (index.getLiteralBoost())
                rankTerms.addItem(new WordItem(toLowerCase(termItem.getRawWord()), index.getName() + "_literal"));
        }
    }

}
