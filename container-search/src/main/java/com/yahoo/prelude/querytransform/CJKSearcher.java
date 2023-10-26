// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.querytransform;

import static com.yahoo.prelude.querytransform.StemmingSearcher.STEMMING;

import java.util.Iterator;
import java.util.ListIterator;

import com.yahoo.language.Language;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.component.chain.dependencies.Provides;
import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.AndSegmentItem;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.PhraseItem;
import com.yahoo.prelude.query.PhraseSegmentItem;
import com.yahoo.prelude.query.SegmentItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.Searcher;
import com.yahoo.search.query.QueryTree;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.PhaseNames;

/**
 * Search to do necessary transforms if the query is in segmented in a CJK language.
 *
 * @author Steinar Knutsen
 */
@After(PhaseNames.UNBLENDED_RESULT)
@Before(STEMMING)
@Provides(CJKSearcher.TERM_ORDER_RELAXATION)
public class CJKSearcher extends Searcher {

    public static final String TERM_ORDER_RELAXATION = "TermOrderRelaxation";

    @Override
    public Result search(Query query, Execution execution) {
        Language language = query.getModel().getParsingLanguage();
        if ( ! language.isCjk()) return execution.search(query);

        QueryTree tree = query.getModel().getQueryTree();
        tree.setRoot(transform(tree.getRoot()));
        query.trace("Rewriting for CJK behavior for implicit phrases", true, 2);
        return execution.search(query);
    }

    private Item transform(Item root) {
        if (root instanceof PhraseItem) {
            PhraseItem asPhrase = (PhraseItem) root;
            if (asPhrase.isExplicit() || hasOverlappingTokens(asPhrase)) return root;

            AndItem replacement = new AndItem();
            for (ListIterator<Item> i = ((CompositeItem) root).getItemIterator(); i.hasNext();) {
                Item item = i.next();
                if (item instanceof WordItem)
                    replacement.addItem(item);
                else if (item instanceof PhraseSegmentItem)
                    replacement.addItem(new AndSegmentItem((PhraseSegmentItem) item));
                else
                    replacement.addItem(item); // should never get here
            }
            return replacement;
        }
        else if (root instanceof PhraseSegmentItem) {
            PhraseSegmentItem asSegment = (PhraseSegmentItem) root;
            if (asSegment.isExplicit() || hasOverlappingTokens(asSegment))
                return root;
            else
                return new AndSegmentItem(asSegment);
        }
        else if (root instanceof SegmentItem) {
            return root; // avoid descending into AndSegmentItems and similar
        }
        else if (root instanceof CompositeItem) {
            for (ListIterator<Item> i = ((CompositeItem) root).getItemIterator(); i.hasNext();) {
                Item item = i.next();
                Item transformedItem = transform(item);
                if (item != transformedItem)
                    i.set(transformedItem);
            }
            return root;
        }
        return root;
    }

    private boolean hasOverlappingTokens(PhraseItem phrase) {
        boolean has = false;
        for (Iterator<Item> i = phrase.getItemIterator(); i.hasNext(); ) {
            Item segment = i.next();
            if (segment instanceof PhraseSegmentItem) has = hasOverlappingTokens((PhraseSegmentItem) segment);
            if (has) return true;
        }
        return has;
    }

    /*
     * We have overlapping tokens (see
     * com.yahoo.prelude.querytransform.test.CJKSearcherTestCase
     * .testCjkQueryWithOverlappingTokens and ParseTestCase for an explanation)
     * if the sum of length of tokens is greater than the length of the original word
     */
    private boolean hasOverlappingTokens(PhraseSegmentItem segments) {
        int segmentsLength=0;
        for (Iterator<Item> i = segments.getItemIterator(); i.hasNext(); ) {
            WordItem segment = (WordItem) i.next();
            segmentsLength += segment.getWord().length();
        }
        return segmentsLength > segments.getRawWord().length();
    }

}
