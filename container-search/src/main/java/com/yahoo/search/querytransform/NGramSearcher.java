// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.querytransform;

import com.yahoo.component.chain.dependencies.After;
import com.yahoo.language.Linguistics;
import com.yahoo.language.process.CharacterClasses;
import com.yahoo.language.process.GramSplitter;
import com.yahoo.prelude.Index;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.hitfield.AnnotateStringFieldPart;
import com.yahoo.prelude.hitfield.JSONString;
import com.yahoo.prelude.hitfield.XMLString;
import com.yahoo.prelude.query.*;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;

import java.util.Iterator;

import static com.yahoo.prelude.searcher.JuniperSearcher.JUNIPER_TAG_REPLACING;
import static com.yahoo.language.LinguisticsCase.toLowerCase;

/**
 * Handles NGram indexes by splitting query terms to them into grams and combining summary field values
 * from such fields into the original text.
 * <p>
 * This declares it must be placed after Juniper searchers because it assumes Juniper token separators
 * (which are returned on bolding) are not replaced by highlight tags when this is run (and "after" means
 * "before" from the point of view of the result).
 *
 * @author bratseth
 */
@After(JUNIPER_TAG_REPLACING)
public class NGramSearcher extends Searcher {

    private final GramSplitter gramSplitter;

    private final CharacterClasses characterClasses;

    public NGramSearcher(Linguistics linguistics) {
        gramSplitter= linguistics.getGramSplitter();
        characterClasses= linguistics.getCharacterClasses();
    }

    @Override
    public Result search(Query query, Execution execution) {
        IndexFacts indexFacts = execution.context().getIndexFacts();
        if ( ! indexFacts.hasNGramIndices()) return execution.search(query); // shortcut

        IndexFacts.Session session = indexFacts.newSession(query);
        boolean rewritten = rewriteToNGramMatching(query.getModel().getQueryTree().getRoot(), 0, session, query);
        if (rewritten)
            query.trace("Rewritten to n-gram matching",true,2);

        Result result=execution.search(query);
        recombineNGrams(result.hits().deepIterator(), session);
        return result;
    }

    @Override
    public void fill(Result result, String summaryClass, Execution execution) {
        execution.fill(result, summaryClass);
        IndexFacts indexFacts = execution.context().getIndexFacts();
        if (indexFacts.hasNGramIndices())
            recombineNGrams(result.hits().deepIterator(), indexFacts.newSession(result.getQuery()));
    }

    private boolean rewriteToNGramMatching(Item item, int indexInParent, IndexFacts.Session indexFacts, Query query) {
        boolean rewritten = false;
        if (item instanceof SegmentItem) { // handle CJK segmented terms which should be grams instead
            SegmentItem segments = (SegmentItem)item;
            Index index = indexFacts.getIndex(segments.getIndexName());
            if (index.isNGram()) {
                Item grams = splitToGrams(segments, toLowerCase(segments.getRawWord()), index.getGramSize(), query);
                replaceItemByGrams(item, grams, indexInParent);
                rewritten = true;
            }
        }
        else if (item instanceof CompositeItem) {
            CompositeItem composite = (CompositeItem)item;
            for (int i=0; i<composite.getItemCount(); i++)
                rewritten = rewriteToNGramMatching(composite.getItem(i), i, indexFacts, query) || rewritten;
        }
        else if (item instanceof TermItem) {
            TermItem term = (TermItem)item;
            Index index = indexFacts.getIndex(term.getIndexName());
            if (index.isNGram()) {
                Item grams = splitToGrams(term,term.stringValue(), index.getGramSize(), query);
                replaceItemByGrams(item, grams, indexInParent);
                rewritten = true;
            }
        }
        return rewritten;
    }

    /**
     * Splits the given item into n-grams and adds them as a CompositeItem containing WordItems searching the
     * index of the input term. If the result is a single gram, that single WordItem is returned rather than the AndItem
     *
     * @param term the term to split, must be an item which implement the IndexedItem and BlockItem "mixins"
     * @param text the text of the item, just stringValue() if the item is a TermItem
     * @param gramSize the gram size to split to
     * @param query the query in which this rewriting is done
     * @return the root of the query subtree produced by this, containing the split items
     */
    protected Item splitToGrams(Item term, String text, int gramSize, Query query) {
        String index = ((HasIndexItem)term).getIndexName();
        CompositeItem gramsItem = createGramRoot(query);
        gramsItem.setIndexName(index);
        Substring origin = ((BlockItem)term).getOrigin();
        for (Iterator<GramSplitter.Gram> i = getGramSplitter().split(text,gramSize); i.hasNext(); ) {
            GramSplitter.Gram gram = i.next();
            WordItem gramWord = new WordItem(gram.extractFrom(text), index, false, origin);
            gramWord.setWeight(term.getWeight());
            gramWord.setProtected(true);
            gramsItem.addItem(gramWord);
        }
        return gramsItem.getItemCount()==1 ? gramsItem.getItem(0) : gramsItem; // return the AndItem, or just the single gram if not multiple
    }

    /**
     * Returns the (thread-safe) object to use to split the query text into grams.
     */
    protected final GramSplitter getGramSplitter() { return gramSplitter; }

    /**
     * Creates the root of the query subtree which will contain the grams to match,
     * called by {@link #splitToGrams}. This hook is provided to make it easy to create a subclass which
     * matches grams using a different composite item, e.g an OrItem.
     * <p>
     * This default implementation return new AndItem();
     *
     * @param query the input query, to make it possible to return a different composite item type
     *        depending on the query content
     * @return the composite item to add the gram items to in {@link #splitToGrams}
     */
    protected CompositeItem createGramRoot(Query query) {
        return new AndItem();
    }

    private void replaceItemByGrams(Item item, Item grams, int indexInParent) {
        if (!(grams instanceof CompositeItem) || !(item.getParent() instanceof PhraseItem)) { // usually, simply replace
            item.getParent().setItem(indexInParent, grams);
        }
        else { // but if the parent is a phrase, we cannot add the AND to it, so add each gram to the phrase
            PhraseItem phraseParent = (PhraseItem)item.getParent();
            phraseParent.removeItem(indexInParent);
            int addedTerms = 0;
            for (Iterator<Item> i = ((CompositeItem)grams).getItemIterator(); i.hasNext(); ) {
                phraseParent.addItem(indexInParent+(addedTerms++),i.next());
            }
        }
    }

    private void recombineNGrams(Iterator<Hit> hits, IndexFacts.Session session) {
        while (hits.hasNext()) {
            Hit hit = hits.next();
            if (hit.isMeta()) continue;
            Object sddocname = hit.getField(Hit.SDDOCNAME_FIELD);
            if (sddocname == null) return;
            for (String fieldName : hit.fieldKeys()) { // TODO: Iterate over indexes instead
                Index index = session.getIndex(fieldName, sddocname.toString());
                if (index.isNGram() && (index.getHighlightSummary() || index.getDynamicSummary())) {
                    hit.setField(fieldName, recombineNGramsField(hit.getField(fieldName), index.getGramSize()));
                }
            }
        }
    }

    private Object recombineNGramsField(Object fieldValue,int gramSize) {
        String recombined=recombineNGrams(fieldValue.toString(),gramSize);
        if (fieldValue instanceof JSONString)
            return new JSONString(recombined);
        else if (fieldValue instanceof XMLString)
            return new XMLString(recombined);
        else
            return recombined;
    }

    /**
     * Converts grams to the original string.
     *
     * Example (gram size 3): <code>blulue rededs</code> becomes <code>blue reds</code>
     */
    private String recombineNGrams(final String string,final int gramSize) {
        StringBuilder b=new StringBuilder();
        int consecutiveWordChars=0;
        boolean inBolding=false;
        MatchTokenStrippingCharacterIterator characters=new MatchTokenStrippingCharacterIterator(string);
        while (characters.hasNext()) {
            char c=characters.next();
            boolean atBoldingSeparator = (c=='\u001f');

            if (atBoldingSeparator && characters.peek()=='\u001f') {
                characters.next();
            }
            else if ( ! characterClasses.isLetterOrDigit(c)) {
                if (atBoldingSeparator)
                    inBolding=!inBolding;
                if ( ! (atBoldingSeparator && nextIsLetterOrDigit(characters)))
                    consecutiveWordChars=0;
                if (inBolding && atBoldingSeparator && areWordCharactersBackwards(gramSize-1,b)) {
                    // we are going to skip characters from a gram, so move bolding start earlier
                    b.insert(b.length()-(gramSize-1),c);
                }
                else {
                    b.append(c);
                }
            }
            else {
                consecutiveWordChars++;
                if (consecutiveWordChars<gramSize || (consecutiveWordChars % gramSize)==0)
                    b.append(c);
            }
        }
        return b.toString();
    }

    private boolean areWordCharactersBackwards(int count,StringBuilder b) {
        for (int i=0; i<count; i++) {
            int checkIndex=b.length()-1-i;
            if (checkIndex<0) return false;
            if ( ! characterClasses.isLetterOrDigit(b.charAt(checkIndex))) return false;
        }
        return true;
    }

    private boolean nextIsLetterOrDigit(MatchTokenStrippingCharacterIterator characters) {
        return characterClasses.isLetterOrDigit(characters.peek());
    }

    /**
     * A string wrapper which skips match token forms marked up Juniper style, such that
     * \uFFF9originalToken\uFFFAtoken\uFFFB is returned as originalToken
     */
    private static class MatchTokenStrippingCharacterIterator {

        private final String s;
        private int current =0;

        public MatchTokenStrippingCharacterIterator(String s) {
            this.s=s;
        }

        public boolean hasNext() {
            skipMarkup();
            return current <s.length();
        }

        public char next() {
            skipMarkup();
            return s.charAt(current++);
        }

        /** Returns the next character without moving to it. Returns \uFFFF if there is no next */
        public char peek() {
            skipMarkup();
            if (s.length()< current +1)
                return '\uFFFF';
            else
                return s.charAt(current);
        }

        private void skipMarkup() {
            if (current>=s.length()) return;
            char c=s.charAt(current);
            if (c== AnnotateStringFieldPart.RAW_ANNOTATE_BEGIN_CHAR) { // skip it
                current++;
            }
            else if (c==AnnotateStringFieldPart.RAW_ANNOTATE_SEPARATOR_CHAR) { // skip to RAW_ANNOTATE_END_CHAR
                do {
                    current++;
                } while (current<s.length() && s.charAt(current)!=AnnotateStringFieldPart.RAW_ANNOTATE_END_CHAR);
                current++; // also skip the RAW_ANNOTATE_END_CHAR
                skipMarkup(); // skip any immediately following markup
            }
        }

    }

}
