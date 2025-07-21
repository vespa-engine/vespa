// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.querytransform;

import java.util.*;

import com.yahoo.component.annotation.Inject;
import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Provides;
import com.yahoo.prelude.Index;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.IndexFacts.Session;
import com.yahoo.prelude.query.*;
import com.yahoo.prelude.query.WordAlternativesItem.Alternative;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.language.Language;
import com.yahoo.language.Linguistics;
import com.yahoo.search.Query;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.PhaseNames;

import static com.yahoo.prelude.querytransform.StemmingSearcher.STEMMING;

/**
 * Normalizes accents
 *
 * @author bratseth
 */
@After({ PhaseNames.UNBLENDED_RESULT, STEMMING })
@Provides(NormalizingSearcher.ACCENT_REMOVAL)
public class NormalizingSearcher extends Searcher {

    public static final String ACCENT_REMOVAL = "AccentRemoval";
    private final Linguistics linguistics;

    @Inject
    public NormalizingSearcher(Linguistics linguistics) {
        this.linguistics = linguistics;
    }

    protected boolean handles(String command) {
        return "normalize".equals(command);
    }

    public String getFunctionName() {
        return "Normalizing accents";
    }

    @Override
    public Result search(Query query, Execution execution) {
        normalize(query, execution.context().getIndexFacts().newSession(query));
        return execution.search(query);
    }

    protected void normalize(Query query, IndexFacts.Session indexFacts) {
        String oldQuery = (query.getTrace().getLevel() >= 2) ? query.getModel().getQueryTree().getRoot().toString() : null;

        normalizeBody(query, indexFacts);

        if (query.getTrace().getLevel() >= 2 && ! query.getModel().getQueryTree().getRoot().toString().equals(oldQuery))
            query.trace(getFunctionName(), true, 2);
    }

    private void normalizeBody(Query query, IndexFacts.Session indexFacts) {
        Item root = query.getModel().getQueryTree().getRoot();
        Language language = query.getModel().getParsingLanguage();
        if (root instanceof BlockItem) {
            List<Item> rootItems = new ArrayList<>(1);
            rootItems.add(root);
            ListIterator<Item> i = rootItems.listIterator();
            i.next();
            normalizeBlocks(language, indexFacts, (BlockItem) root, i);
            if ( ! rootItems.isEmpty()) // give up normalizing if the root was removed
                query.getModel().getQueryTree().setRoot(rootItems.get(0));
        } else if (root instanceof CompositeItem) {
            query.getModel().getQueryTree().setRoot(normalizeComposite(language, indexFacts, (CompositeItem) root));
        }
    }
    
    private Item normalizeComposite(Language language, IndexFacts.Session indexFacts, CompositeItem item) {
        if (item instanceof PhraseItem phrase)  {
            return normalizePhrase(language, indexFacts, phrase);
        }
        else {
            for (ListIterator<Item> i = item.getItemIterator(); i.hasNext(); ) {
                Item current = i.next();

                if (current instanceof BlockItem block) {
                    normalizeBlocks(language, indexFacts, block, i);
                } else if (current instanceof CompositeItem composite) {
                    Item currentProcessed = normalizeComposite(language, indexFacts, composite);
                    i.set(currentProcessed);
                }
            }
            return item;
        }
    }

    private void normalizeBlocks(Language language, IndexFacts.Session indexFacts, BlockItem block, ListIterator<Item> i) {
        if (block instanceof TermItem term) {
            if (block instanceof WordAlternativesItem alternatives) {
                normalizeAlternatives(language, indexFacts, alternatives);
            } else {
                normalizeWord(language, indexFacts, term, i);
            }
        } else {
            for (ListIterator<Item> j = ((SegmentItem) block).getItemIterator(); j.hasNext();)
                normalizeWord(language, indexFacts, (TermItem) j.next(), j);
        }
    }

    private void normalizeAlternatives(Language language, Session indexFacts, WordAlternativesItem block) {
        if ( ! block.isNormalizable()) return;

        Index index = indexFacts.getIndex(block.getIndexName());
        if (index.isAttribute()) return;
        if ( ! index.getNormalize()) return;

        List<Alternative> terms = block.getAlternatives();
        for (Alternative term : terms) {
            String accentDropped = linguistics.getTransformer().accentDrop(term.word, language);
            if ( ! term.word.equals(accentDropped) && !accentDropped.isEmpty())
                block.addTerm(accentDropped, term.exactness * .7d);
        }
    }

    private Item normalizePhrase(Language language, IndexFacts.Session indexFacts, PhraseItem phrase) {
        if ( ! indexFacts.getIndex(phrase.getIndexName()).getNormalize()) return phrase;

        for (ListIterator<Item> i = phrase.getItemIterator(); i.hasNext();) {
            IndexedItem content = (IndexedItem)i.next();

            if (content instanceof TermItem term) {
                normalizeWord(language, indexFacts, term, i);
            }
            else {
                for (ListIterator<Item> j = ((PhraseSegmentItem)content).getItemIterator(); j.hasNext();)
                    normalizeWord(language, indexFacts, (TermItem) j.next(), j);
            }
        }
        return phrase;
    }

    private void normalizeWord(Language language, IndexFacts.Session indexFacts, TermItem term, ListIterator<Item> i) {
        if ( ! (term instanceof WordItem word)) return;
        if ( ! term.isNormalizable()) return;
        Index index = indexFacts.getIndex(term.getIndexName());
        if (index.isAttribute()) return;
        if ( ! index.getNormalize()) return;

        String accentDropped = linguistics.getTransformer().accentDrop(word.getWord(), language);
        if (accentDropped.isEmpty())
            i.remove();
        else
            word.setWord(accentDropped);
    }

}
