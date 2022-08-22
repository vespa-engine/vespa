// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.parser;

import com.yahoo.collections.Pair;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.AndSegmentItem;
import com.yahoo.prelude.query.BlockItem;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.IntItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.MarkerWordItem;
import com.yahoo.prelude.query.PhraseItem;
import com.yahoo.prelude.query.PhraseSegmentItem;
import com.yahoo.prelude.query.PrefixItem;
import com.yahoo.prelude.query.SegmentItem;
import com.yahoo.prelude.query.Substring;
import com.yahoo.prelude.query.SubstringItem;
import com.yahoo.prelude.query.SuffixItem;
import com.yahoo.prelude.query.TaggableItem;
import com.yahoo.prelude.query.TermItem;
import com.yahoo.prelude.query.UriItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.query.parser.ParserEnvironment;

import java.util.ArrayList;
import java.util.List;

import static com.yahoo.prelude.query.parser.Token.Kind.*;

/**
 * Base class for parsers of the query languages which can be used
 * for structured queries (types ANY, ALL and ADVANCED).
 *
 * @author Steinar Knutsen
 * @author bratseth
 */
abstract class StructuredParser extends AbstractParser {

    protected StructuredParser(ParserEnvironment environment) {
        super(environment);
    }

    protected abstract Item handleComposite(boolean topLevel);

    protected Item compositeItem() {
        int position = tokens.getPosition();
        Item item = null;

        try {
            tokens.skipMultiple(PLUS);
            if (!tokens.skip(LBRACE)) {
                return null;
            }

            item = handleComposite(false);

            tokens.skip(RBRACE);
            return item;
        } finally {
            if (item == null) {
                tokens.setPosition(position);
            }
        }
    }

    /** Sets the submodes used for url parsing. Override this to influence when such submodes are used. */
    protected void setSubmodeFromIndex(String indexName, IndexFacts.Session indexFacts) {
        submodes.setFromIndex(indexName, indexFacts);
    }

    /**
     * Returns an item and whether it had an explicit index ('indexname:' prefix).
     *
     * @return an item and whether it has an explicit index, or a Pair with the first element null if none
     */
    protected Pair<Item, Boolean> indexableItem() {
        int position = tokens.getPosition();
        Item item = null;

        try {
            boolean explicitIndex = false;
            String indexName = indexPrefix();
            if (indexName != null)
                explicitIndex = true;
            else
                indexName = this.defaultIndex;
            setSubmodeFromIndex(indexName, indexFacts);

            item = number();

            if (item == null) {
                item = phrase(indexName);
            }

            if (item == null && indexName != null) {
                if (wordsAhead()) {
                    item = phrase(indexName);
                }
            }

            submodes.reset();

            int weight = -1;

            if (item != null) {
                weight = weightSuffix();
            }
            if (indexName != null && item != null) {
                item.setIndexName(indexName);
            }

            if (weight != -1 && item != null) {
                item.setWeight(weight);
            }

            return new Pair<>(item, explicitIndex);
        } finally {
            if (item == null) {
                tokens.setPosition(position);
            }
        }
    }

    // scan forward for terms while ignoring noise
    private boolean wordsAhead() {
        while (tokens.hasNext()) {
            if (tokens.currentIsNoIgnore(SPACE)) {
                return false;
            }
            if (tokens.currentIsNoIgnore(NUMBER) || tokens.currentIsNoIgnore(WORD)) {
                return true;
            }
            tokens.skipNoIgnore();
        }
        return false;
    }

    // wordsAhead and nothingAhead... uhm... so similar...
    private boolean nothingAhead(boolean skip) {
        int position = tokens.getPosition();
        try {
            boolean quoted = false;
            while (tokens.hasNext()) {
                if (tokens.currentIsNoIgnore(QUOTE)) {
                    tokens.skipMultiple(QUOTE);
                    quoted = !quoted;
                } else {
                    if (!quoted && tokens.currentIsNoIgnore(SPACE)) {
                        return true;
                    }
                    if (tokens.currentIsNoIgnore(NUMBER)
                            || tokens.currentIsNoIgnore(WORD)) {
                        return false;
                    }
                    tokens.skipNoIgnore();
                }
            }
            return true;
        } finally {
            if (!skip) {
                tokens.setPosition(position);
            }
        }
    }

    private String indexPrefix() {
        int position = tokens.getPosition();
        String item = null;

        try {
            List<Token> firstWord = new ArrayList<>();
            List<Token> secondWord = new ArrayList<>();

            tokens.skip(LSQUAREBRACKET);

            if ( ! tokens.currentIs(WORD) && ! tokens.currentIs(NUMBER) && ! tokens.currentIs(UNDERSCORE)) {
                return null;
            }

            firstWord.add(tokens.next());

            while (tokens.currentIsNoIgnore(UNDERSCORE)
                   || tokens.currentIsNoIgnore(WORD)
                   || tokens.currentIsNoIgnore(NUMBER)) {
                firstWord.add(tokens.next());
            }

            while (tokens.currentIsNoIgnore(DOT)) {
                secondWord.add(tokens.next());
                if (tokens.currentIsNoIgnore(WORD) || tokens.currentIsNoIgnore(NUMBER)) {
                    secondWord.add(tokens.next());
                } else {
                    return null;
                }
                while (tokens.currentIsNoIgnore(UNDERSCORE)
                       || tokens.currentIsNoIgnore(WORD)
                       || tokens.currentIsNoIgnore(NUMBER)) {
                    secondWord.add(tokens.next());
                }
            }

            if ( ! tokens.skipNoIgnore(COLON))
                return null;

            item = concatenate(firstWord) + concatenate(secondWord);

            item = indexFacts.getCanonicName(item);

            if ( ! indexFacts.isIndex(item)) { // Only if this really is an index
                // Marker for the finally block
                item = null;
                return null;
            } else {
                if (nothingAhead(false)) {
                    // correct index syntax, correct name, but followed by noise. Let's skip this.
                    nothingAhead(true);
                    position = tokens.getPosition();
                    item = indexPrefix();
                }
            }
            return item;
        } finally {
            if (item == null) {
                tokens.setPosition(position);
            }
        }
    }

    private String concatenate(List<Token> tokens) {
        StringBuilder s = new StringBuilder();
        for (Token t : tokens) {
            s.append(t.toString());
        }
        return s.toString();
    }

    /** Returns the specified term weight, or -1 if there is no weight suffix */
    private int weightSuffix() {
        int position = tokens.getPosition();
        int item = -1;

        try {
            if (!tokens.skipNoIgnore(EXCLAMATION)) {
                return -1;
            }
            item = 150;

            if (tokens.currentIsNoIgnore(NUMBER)) {
                try {
                    item = Integer.parseInt(tokens.next().toString());
                } catch (NumberFormatException e) {
                    item = -1;
                }
            } else {
                while (tokens.currentIsNoIgnore(EXCLAMATION)) {
                    item += 50;
                    tokens.skipNoIgnore();
                }
            }
            return item;

        } finally {
            if (item == -1) {
                tokens.setPosition(position);
            }
        }
    }

    private boolean endOfNumber() {
        return tokens.currentIsNoIgnore(SPACE)
               || tokens.currentIsNoIgnore(RSQUAREBRACKET)
               || tokens.currentIsNoIgnore(SEMICOLON)
               || tokens.currentIsNoIgnore(RBRACE)
               || tokens.currentIsNoIgnore(EOF)
               || tokens.currentIsNoIgnore(EXCLAMATION);
    }

    private String decimalPart() {
        int position = tokens.getPosition();
        boolean consumed = false;

        try {
            if (!tokens.skipNoIgnore(DOT)) return "";
            if (tokens.currentIsNoIgnore(NUMBER)) {
                consumed = true;
                return "." + tokens.next().toString();
            }
            return "";
        } finally {
            if ( ! consumed)
                tokens.setPosition(position);
        }
    }

    private IntItem number() {
        int position = tokens.getPosition();
        IntItem item = null;

        try {
            item = numberRange();

            tokens.skip(LSQUAREBRACKET);
            if (item == null)
                tokens.skipNoIgnore(SPACE);
            // TODO: Better definition of start and end of numeric items
            if (item == null && tokens.currentIsNoIgnore(MINUS) && (tokens.currentNoIgnore(1).kind == NUMBER)) {
                tokens.skipNoIgnore();
                Token t = tokens.next();
                item = new IntItem("-" + t.toString() + decimalPart(), true);
                item.setOrigin(t.substring);
            } else if (item == null && tokens.currentIs(NUMBER)) {
                Token t = tokens.next();
                item = new IntItem(t.toString() + decimalPart(), true);
                item.setOrigin(t.substring);
            }

            if (item == null) {
                item = numberSmaller();
            }

            if (item == null) {
                item = numberGreater();
            }
            if (item != null && ! endOfNumber()) {
                item = null;
            }
            return item;
        } finally {
            if (item == null) {
                tokens.setPosition(position);
            }
        }
    }

    private IntItem numberRange() {
        int position = tokens.getPosition();
        IntItem item = null;

        try {
            Token initial = tokens.next();
            if (initial.kind != LSQUAREBRACKET) return null;

            String rangeStart = "";
            boolean negative = tokens.skip(MINUS);

            if (tokens.currentIs(NUMBER)) {
                rangeStart = (negative ? "-" : "") + tokens.next().toString() + decimalPart();
            }

            if (!tokens.skip(SEMICOLON)) return null;

            String rangeEnd = "";

            negative = tokens.skip(MINUS);

            if (tokens.currentIs(NUMBER)) {
                rangeEnd = (negative ? "-" : "") + tokens.next().toString() + decimalPart();
            }
            if (rangeStart.isBlank() && rangeEnd.isBlank()) return null;


            String range = "[" + rangeStart + ";" + rangeEnd;
            if (tokens.skip(SEMICOLON)) {
                negative = tokens.skip(MINUS);
                if (tokens.currentIs(NUMBER)) {
                    String rangeLimit = (negative ? "-" : "") + tokens.next().toString();
                    range += ";" + rangeLimit;
                }
            }
            tokens.skip(RSQUAREBRACKET);

            item = new IntItem(range + "]", true);
            item.setOrigin(new Substring(initial.substring.start, tokens.currentNoIgnore().substring.start,
                                         initial.getSubstring().getSuperstring())); // XXX: Unsafe end?

            return item;
        } finally {
            if (item == null) {
                tokens.setPosition(position);
            }
        }
    }

    private IntItem numberSmaller() {
        int position = tokens.getPosition();
        IntItem item = null;

        try {
            Token initial = tokens.next();
            if (initial.kind != SMALLER) return null;

            boolean negative = tokens.skipNoIgnore(MINUS);
            if ( ! tokens.currentIs(NUMBER)) return null;

            item = new IntItem("<" + (negative ? "-" : "") + tokens.next() + decimalPart(), true);
            item.setOrigin(new Substring(initial.substring.start, tokens.currentNoIgnore().substring.start,
                                         initial.getSubstring().getSuperstring())); // XXX: Unsafe end?
            return item;
        } finally {
            if (item == null) {
                tokens.setPosition(position);
            }
        }
    }

    private IntItem numberGreater() {
        int position = tokens.getPosition();
        IntItem item = null;

        try {
            Token initial = tokens.next();
            if (initial.kind != GREATER) return null;

            boolean negative = tokens.skipNoIgnore(MINUS);
            if ( ! tokens.currentIs(NUMBER)) return null;

            item = new IntItem(">" + (negative ? "-" : "") + tokens.next() + decimalPart(), true);
            item.setOrigin(new Substring(initial.substring.start, tokens.currentNoIgnore().substring.start,
                                         initial.getSubstring().getSuperstring())); // XXX: Unsafe end?
            return item;
        } finally {
            if (item == null) {
                tokens.setPosition(position);
            }
        }
    }

    /**
     * Words for phrases also permits numerals as words
     *
     * @param quoted whether we are consuming text within quoted
     * @param insidePhrase whether we are consuming additional items for an existing phrase
     */
    private Item phraseWord(String indexName, boolean quoted, boolean insidePhrase) {
        int position = tokens.getPosition();
        Item item = null;

        try {
            item = word(indexName, quoted);

            if (item == null && tokens.currentIs(NUMBER)) {
                Token t = tokens.next();
                if (insidePhrase) {
                    item = new WordItem(t, true);
                } else {
                    item = new IntItem(t.toString(), true);
                    ((TermItem) item).setOrigin(t.substring);
                }
            }

            return item;
        } finally {
            if (item == null) {
                tokens.setPosition(position);
            }
        }
    }

    /**
     * Returns a WordItem if this is a non CJK query,
     * a WordItem or SegmentItem if this is a CJK query,
     * null if the current item is not a word
     *
     * @param quoted whether this token is inside quotes
     */
    protected Item word(String indexName, boolean quoted) {
        int position = tokens.getPosition();
        Item item = null;

        try {
            if ( ! tokens.currentIs(WORD)
                && ((!tokens.currentIs(NUMBER) && !tokens.currentIs(MINUS)
                && !tokens.currentIs(UNDERSCORE)) || (!submodes.url && !submodes.site))) {
                return null;
            }
            Token word = tokens.next();

            if (submodes.url) {
                item = new WordItem(word, true);
            } else {
                item = segment(indexName, word, quoted);
            }

            if (submodes.url || submodes.site) {
                StringBuilder buffer = null;
                Token token = tokens.currentNoIgnore();

                while (token.kind == WORD || token.kind == NUMBER || token.kind == MINUS || token.kind == UNDERSCORE) {
                    if (buffer == null) {
                        buffer = getStringContents(item);
                    }
                    buffer.append(token);
                    tokens.skipNoIgnore();
                    token = tokens.currentNoIgnore();
                }
                if (buffer != null) {
                    Substring termSubstring = ((BlockItem) item).getOrigin();
                    Substring substring = new Substring(termSubstring.start, token.substring.start, termSubstring.getSuperstring()); // XXX: Unsafe end?
                    String str = buffer.toString();
                    item = new WordItem(str, "", true, substring);
                }
            }
            return item;
        } finally {
            if (item == null) {
                tokens.setPosition(position);
            }
        }
    }

    private StringBuilder getStringContents(Item item) {
        if (item instanceof TermItem) {
            return new StringBuilder(
                    ((TermItem) item).stringValue());
        } else if (item instanceof SegmentItem) {
            return new StringBuilder(
                    ((SegmentItem) item).getRawWord());
        } else {
            throw new RuntimeException("Parser bug. Unexpected item type, send stack trace in a bug ticket to the Vespa team.");
        }
    }


    /**
     * An phrase or word, either marked by quotes or by non-spaces between
     * words or by a combination.
     *
     * @param indexName the index name which preceeded this phrase, or null if none
     * @return a word if there's only one word, a phrase if there is
     *         several quoted or non-space-separated words, or null otherwise
     */
    private Item phrase(String indexName) {
        int position = tokens.getPosition();
        Item item = null;

        try {
            item = phraseBody(indexName);
            return item;
        } finally {
            if (item == null) {
                tokens.setPosition(position);
            }
        }
    }

    /** Returns a word, a phrase, or another composite */
    private Item phraseBody(String indexName) {
        boolean quoted = false;
        CompositeItem composite = null;
        Item firstWord = null;
        boolean starAfterFirst = false;
        boolean starBeforeFirst;

        if (tokens.skipMultiple(QUOTE)) {
            quoted = !quoted;
        }
        boolean addStartOfHostMarker = addStartMarking();

        braceLevelURL = 0;

        do {
            starBeforeFirst = tokens.skip(STAR);

            if (tokens.skipMultiple(QUOTE)) {
                quoted = !quoted;
            }

            Item word = phraseWord(indexName, quoted, (firstWord != null) || (composite != null));

            if (word == null) {
                if (tokens.skipMultiple(QUOTE)) {
                    quoted = !quoted;
                }
                if (quoted && tokens.hasNext()) {
                    tokens.skipNoIgnore();
                    continue;
                } else {
                    break;
                }
            } else if (quoted && word instanceof PhraseSegmentItem) {
                ((PhraseSegmentItem) word).setExplicit(true);
            }

            if (composite != null) {
                composite.addItem(word);
                connectLastTermsIn(composite);
            } else if (firstWord != null) {
                if (submodes.site || submodes.url) {
                    UriItem uriItem = new UriItem();
                    if (submodes.site)
                        uriItem.setEndAnchorDefault(true);
                    composite = uriItem;
                }
                else {
                    if (quoted || indexFacts.getIndex(indexName).getPhraseSegmenting())
                        composite = new PhraseItem();
                    else
                        composite = new AndItem();
                }

                if ( (quoted || submodes.site || submodes.url) && composite instanceof PhraseItem) {
                    ((PhraseItem)composite).setExplicit(true);
                }
                if (addStartOfHostMarker) {
                    composite.addItem(MarkerWordItem.createStartOfHost());
                }
                if (firstWord instanceof IntItem) {
                    IntItem asInt = (IntItem) firstWord;
                    firstWord = new WordItem(asInt.stringValue(), asInt.getIndexName(),
                                             true, asInt.getOrigin());
                }
                composite.addItem(firstWord);
                composite.addItem(word);
                connectLastTermsIn(composite);
            } else if (word instanceof PhraseItem) {
                composite = (PhraseItem)word;
            } else {
                firstWord = word;
                starAfterFirst = tokens.skipNoIgnore(STAR);
            }
            if (!quoted && tokens.currentIs(QUOTE)) {
                break;
            }

            boolean atWord = skipToNextPhraseWord(quoted);

            if (!atWord && tokens.skipMultipleNoIgnore(QUOTE)) {
                quoted = !quoted;
            }

            if (!atWord && !quoted) {
                break;
            }

            if (quoted && tokens.skipMultiple(QUOTE)) {
                break;
            }

        } while (tokens.hasNext());

        braceLevelURL = 0;

        if (composite != null) {
            if (addEndMarking()) {
                composite.addItem(MarkerWordItem.createEndOfHost());
            }
            return composite;
        } else if (firstWord != null && submodes.site) {
            if (starAfterFirst && !addStartOfHostMarker) {
                return firstWord;
            } else {
                composite = new PhraseItem();
                ((PhraseItem)composite).setExplicit(true);
                if (addStartOfHostMarker) {
                    composite.addItem(MarkerWordItem.createStartOfHost());
                }
                if (firstWord instanceof IntItem) {
                    IntItem asInt = (IntItem) firstWord;
                    firstWord = new WordItem(asInt.stringValue(), asInt.getIndexName(), true, asInt.getOrigin());
                }
                composite.addItem(firstWord);
                if (!starAfterFirst) {
                    composite.addItem(MarkerWordItem.createEndOfHost());
                }
                return composite;
            }
        } else {
            if (firstWord != null && firstWord instanceof TermItem && (starAfterFirst || starBeforeFirst)) {
                // prefix, suffix or substring
                TermItem firstTerm = (TermItem) firstWord;
                if (starAfterFirst) {
                    if (starBeforeFirst) {
                        return new SubstringItem(firstTerm.stringValue(), true);
                    } else {
                        return new PrefixItem(firstTerm.stringValue(), true);
                    }
                } else {
                    return new SuffixItem(firstTerm.stringValue(), true);
                }
            }
            return firstWord;
        }
    }

    private void connectLastTermsIn(CompositeItem composite) {
        int items = composite.items().size();
        if (items < 2) return;
        Item nextToLast = composite.items().get(items - 2);
        if (nextToLast instanceof AndSegmentItem) {
            var subItems = ((AndSegmentItem) nextToLast).items();
            nextToLast = subItems.get(subItems.size() - 1);
        }
        if ( ! (nextToLast instanceof TermItem)) return;
        Item last = composite.items().get(items - 1);
        if (last instanceof AndSegmentItem) {
            last = ((AndSegmentItem) last).items().get(0);
        }
        if (last instanceof TaggableItem) {
            TermItem t1 = (TermItem) nextToLast;
            t1.setConnectivity(last, 1);
        }
    }

    private boolean addStartMarking() {
        if (submodes.explicitAnchoring() && tokens.currentIs(HAT)) {
            tokens.skip();
            return true;
        }
        return false;
    }

    private boolean addEndMarking() {
        if (submodes.explicitAnchoring() && tokens.currentIs(DOLLAR)) {
            tokens.skip();
            return true;
        } else if (submodes.site && tokens.currentIs(STAR)) {
            tokens.skip();
            return false;
        } else if (submodes.site && !tokens.currentIs(DOT)) {
            return true;
        }
        return false;
    }

    /**
     * Skips one or multiple phrase separators
     *
     * @return true if the item we land at after skipping zero or more is
     *         a phrase word
     */
    private boolean skipToNextPhraseWord(boolean quoted) {
        boolean skipped = false;

        do {
            skipped = false;
            if (submodes.url) {
                if (tokens.currentIsNoIgnore(RBRACE)) {
                    braceLevelURL--;
                }
                if (tokens.currentIsNoIgnore(LBRACE)) {
                    braceLevelURL++;
                }
                if (tokens.hasNext() && !tokens.currentIsNoIgnore(SPACE) && braceLevelURL >= 0) {
                    tokens.skip();
                    skipped = true;
                }
            } else if (submodes.site) {
                if (tokens.hasNext() && !tokens.currentIsNoIgnore(SPACE)
                        && !tokens.currentIsNoIgnore(STAR)
                        && !tokens.currentIsNoIgnore(HAT)
                        && !tokens.currentIsNoIgnore(DOLLAR)
                        && !tokens.currentIsNoIgnore(RBRACE)) {
                    tokens.skip();
                    skipped = true;
                }
            } else {
                if (tokens.skipMultipleNoIgnore(DOT)) {
                    skipped = true;
                }
                if (tokens.skipMultipleNoIgnore(COMMA)) {
                    skipped = true;
                }
                if (tokens.skipMultipleNoIgnore(PLUS)) {
                    skipped = true;
                }
                if (tokens.skipMultipleNoIgnore(MINUS)) {
                    skipped = true;
                }
                if (tokens.skipMultipleNoIgnore(UNDERSCORE)) {
                    skipped = true;
                }
                if (tokens.skipMultipleNoIgnore(HAT)) {
                    skipped = true;
                }
                if (tokens.skipMultipleNoIgnore(DOLLAR)) {
                    skipped = true;
                }
                ;
                if (tokens.skipMultipleNoIgnore(STAR)) {
                    skipped = true;
                }
                if (tokens.skipMultipleNoIgnore(COLON)) {
                    skipped = true;
                }
                if (quoted) {
                    if (tokens.skipMultipleNoIgnore(RBRACE)) {
                        skipped = true;
                    }
                    if (tokens.skipMultipleNoIgnore(LBRACE)) {
                        skipped = true;
                    }
                }
                if (tokens.skipMultipleNoIgnore(NOISE)) {
                    skipped = true;
                }
            }
        } while (skipped && !tokens.currentIsNoIgnore(WORD)
                && !tokens.currentIsNoIgnore(NUMBER) && !URLModeWordChar());

        return tokens.currentIsNoIgnore(WORD)
                || tokens.currentIsNoIgnore(NUMBER) || URLModePhraseChar();
    }

    private boolean URLModeWordChar() {
        if (!submodes.url) {
            return false;
        }
        return tokens.currentIsNoIgnore(UNDERSCORE)
                || tokens.currentIsNoIgnore(MINUS);
    }

    private boolean URLModePhraseChar() {
        if (!submodes.url) {
            return false;
        }
        return !(tokens.currentIsNoIgnore(RBRACE)
                || tokens.currentIsNoIgnore(SPACE));
    }


}
