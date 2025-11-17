// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.querytransform;

import static com.yahoo.prelude.querytransform.StemmingSearcher.STEMMING;

import java.util.Iterator;
import java.util.ListIterator;

import com.yahoo.language.Language;
import com.yahoo.prelude.IndexFacts;
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
 * Does necessary transforms if the query is in segmented in a CJK language.
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
        tree.setRoot(transform(tree.getRoot(), execution.context().getIndexFacts().newSession(query)));
        query.trace("Rewriting for CJK behavior for implicit phrases", true, 2);
        return execution.search(query);
    }

    private Item transform(Item item, IndexFacts.Session indexFacts) {
        if (item instanceof PhraseItem phrase) {
            if (phrase.isExplicit()) return item;
            if (indexFacts.getIndex(phrase.getIndexName()).isNGram()) return item;
            if (hasOverlappingTokens(phrase)) return item;

            AndItem replacement = new AndItem();
            for (ListIterator<Item> i = phrase.getItemIterator(); i.hasNext();) {
                Item child = i.next();
                if (child instanceof WordItem)
                    replacement.addItem(child);
                else if (child instanceof PhraseSegmentItem asSegment)
                    replacement.addItem(new AndSegmentItem(asSegment));
                else
                    replacement.addItem(child); // should never get here
            }
            return replacement;
        }
        else if (item instanceof PhraseSegmentItem segment) {
            if (segment.isExplicit() || hasOverlappingTokens(segment))
                return item;
            else
                return new AndSegmentItem(segment);
        }
        else if (item instanceof SegmentItem) {
            return item; // avoid descending into AndSegmentItems and similar
        }
        else if (item instanceof CompositeItem composite) {
            for (ListIterator<Item> i = composite.getItemIterator(); i.hasNext();) {
                Item child = i.next();
                Item transformedItem = transform(child, indexFacts);
                if (child != transformedItem && composite.acceptsItemsOfType(transformedItem.getItemType()))
                    i.set(transformedItem);
            }
            return item;
        }
        return item;
    }

    private boolean hasOverlappingTokens(PhraseItem phrase) {
        for (Iterator<Item> i = phrase.getItemIterator(); i.hasNext(); ) {
            Item segment = i.next();
            if (segment instanceof PhraseSegmentItem && hasOverlappingTokens((PhraseSegmentItem) segment))
                return true;
        }
        return false;
    }

    /*
     * We have overlapping tokens (see
     * com.yahoo.prelude.querytransform.test.CJKSearcherTestCase
     * .testCjkQueryWithOverlappingTokens and ParseTestCase for an explanation)
     * if the sum of length of tokens is greater than the length of the original word
     */
    private boolean hasOverlappingTokens(PhraseSegmentItem segments) {
        int segmentsLength = 0;
        for (Iterator<Item> i = segments.getItemIterator(); i.hasNext(); ) {
            Item item = i.next();
            if (item instanceof WordItem wordItem) {
                segmentsLength += wordItem.getWord().length();
            }
        }
        return segmentsLength > segments.getRawWord().length();
    }

}
