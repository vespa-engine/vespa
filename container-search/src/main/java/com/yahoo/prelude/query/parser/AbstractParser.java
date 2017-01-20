// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.parser;

import com.yahoo.language.Language;
import com.yahoo.language.process.Segmenter;
import com.yahoo.log.event.*;
import com.yahoo.prelude.Index;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.query.*;
import com.yahoo.search.query.QueryTree;
import com.yahoo.search.query.parser.Parsable;
import com.yahoo.search.query.parser.ParserEnvironment;

import java.util.*;

/**
 * The Vespa query parser.
 *
 * @author bratseth
 * @author Steinar Knutsen
 */
@SuppressWarnings("deprecation")
public abstract class AbstractParser implements CustomParser {

    /** The current submodes of this parser */
    protected Submodes submodes = new Submodes();

    /**
     * The current language of this parser. Used to decide whether and how to
     * use the CJKSegmenter
     */
    protected Language language = Language.UNKNOWN;

    /** The IndexFacts.Session of this query */
    protected IndexFacts.Session indexFacts;

    /**
     * The counter for braces in URLs, braces in URLs are accepted so long as
     * they are balanced.
     */
    protected int braceLevelURL = 0;

    protected final ParserEnvironment environment;
    protected final TokenPosition tokens = new TokenPosition();

    /**
     * An enumeration of the parser index-controlled submodes. Any combination
     * of these may be active at the same time. SubModes are activated or
     * deactivated by specifying special indexes in the query.
     */
    final class Submodes {

        /**
         * Url mode allows "_" and "-" as word characters. Default is false
         */
        public boolean url = false;

        /**
         * Site mode - host names get start of host and end of host markers.
         * Default is false
         */
        public boolean site = false;

        /**
         * Sets submodes from an index.
         *
         * @param indexName the index name which should decide the submodes, or null to do nothing.
         * @param session the session used to look up information about this index
         */
        @SuppressWarnings({"deprecation"})
        // To avoid this we need to pass an IndexFacts.session down instead - easily done but not without breaking API's
        public void setFromIndex(final String indexName, IndexFacts.Session session) {
            if (indexName == null) {
                return;
            }

            reset();

            final Index current = session.getIndex(indexName);

            if (current.isUriIndex()) {
                url = true;
            } else if (current.isHostIndex()) {
                site = true;
            }
        }

        /** Sets default values for all submodes */
        public void reset() {
            url = false;
            site = false;
        }

        /**
         * Returns whether we are in a mode which allows explicit anchoring
         * markers, ^ and $
         *
         * @return True if we are doing explicit anchoring.
         */
        public boolean explicitAnchoring() {
            return site;
        }
    }

    /**
     * <p>Creates a new instance of this class, storing the given {@link ParserEnvironment} for parse-time access to the
     * environment.</p>
     *
     * @param environment The environment settings to attach to the Parser.
     */
    protected AbstractParser(ParserEnvironment environment) {
        this.environment = ParserEnvironment.fromParserEnvironment(environment);
        if (this.environment.getIndexFacts() == null) {
            this.environment.setIndexFacts(new IndexFacts());
        }
    }

    @Override
    public final QueryTree parse(Parsable query) {
        Item root = null;
        if (query != null) {
            root = parse(query.getQuery(),
                         query.getFilter(),
                         query.getLanguage(),
                         environment.getIndexFacts().newSession(query.getSources(), query.getRestrict()),
                         query.getDefaultIndexName());
        }
        if (root == null) {
            root = new NullItem();
        }
        return new QueryTree(root);
    }

    @Override
    public final Item parse(String queryToParse, String filterToParse, Language parsingLanguage,
                            IndexFacts.Session indexFacts, String defaultIndexName) {
        if (queryToParse == null) {
            return null;
        }
        if (parsingLanguage == null) {
            parsingLanguage = environment.getLinguistics().getDetector().detect(queryToParse, null).getLanguage();
        }
        setState(parsingLanguage, indexFacts);
        tokenize(queryToParse, defaultIndexName, indexFacts);
        Item root = parseItems();
        if (filterToParse != null) {
            AnyParser filterParser = new AnyParser(environment);
            if (root == null) {
                root = filterParser.parseFilter(filterToParse, parsingLanguage, indexFacts);
            } else {
                root = filterParser.applyFilter(root, filterToParse, parsingLanguage, indexFacts);
            }
        }
        root = simplifyPhrases(root);
        if (defaultIndexName != null) {
            assignDefaultIndex(indexFacts.getCanonicName(defaultIndexName), root);
        }
        return root;
    }

    protected abstract Item parseItems();

    /**
     * Assigns the default index to query terms having no default index The
     * parser _should_ have done this, for some reason it doesn't
     *
     * @param defaultIndex The default index to assign.
     * @param item         The item to check.
     */
    private static void assignDefaultIndex(final String defaultIndex,
            final Item item) {
        if (defaultIndex == null || item == null) {
            return;
        }

        if (item instanceof IndexedItem) {
            final IndexedItem indexName = (IndexedItem) item;

            if ("".equals(indexName.getIndexName())) {
                indexName.setIndexName(defaultIndex);
            }
        } else if (item instanceof CompositeItem) {
            final Iterator<Item> items = ((CompositeItem) item)
                    .getItemIterator();
            while (items.hasNext()) {
                final Item i = items.next();
                assignDefaultIndex(defaultIndex, i);
            }
        }
    }

    /**
     * Unicode normalizes some piece of natural language text. The chosen form
     * is compatibility decomposition, canonical composition (NFKC).
     *
     * @param input The string to normalize.
     * @return The normalized string.
     */
    protected String normalize(String input) {
        if (input == null || input.length() == 0) {
            return input;
        }
        return environment.getLinguistics().getNormalizer().normalize(input);
    }

    protected void setState(Language queryLanguage, IndexFacts.Session indexFacts) {
        this.indexFacts = indexFacts;
        language = queryLanguage;
        submodes.reset();
    }

    /**
     * Tokenizes the given string and initializes tokens with the found tokens.
     *
     * @param query            the string to tokenize.
     * @param defaultIndexName the name of the index to use as default.
     * @param indexFacts       resolved information about the index we are searching
     */
    protected void tokenize(String query, String defaultIndexName, IndexFacts.Session indexFacts) {
        Tokenizer tokenizer = new Tokenizer(environment.getLinguistics());
        tokenizer.setSubstringSpecialTokens(language.isCjk());
        tokenizer.setSpecialTokens(environment.getSpecialTokens());
        tokens.initialize(tokenizer.tokenize(query, defaultIndexName, indexFacts));
    }

    /**
     * Collapses single item phrases in the tree to the contained item.
     *
     * @param unwashed The item whose phrases to simplify.
     * @return The simplified item.
     */
    public static Item simplifyPhrases(Item unwashed) {
        if (unwashed == null) {
            return unwashed;
        } else if (unwashed instanceof PhraseItem) {
            return collapsePhrase((PhraseItem) unwashed);
        } else if (unwashed instanceof CompositeItem) {
            CompositeItem composite = (CompositeItem) unwashed;
            ListIterator<Item> i = composite.getItemIterator();

            while (i.hasNext()) {
                Item original = i.next();
                Item transformed = simplifyPhrases(original);

                if (original != transformed) {
                    i.set(transformed);
                }
            }
            return unwashed;
        } else {
            return unwashed;
        }
    }

    private static Item collapsePhrase(PhraseItem phrase) {
        if (phrase.getItemCount() == 1 && phrase.getItem(0) instanceof WordItem) {
            // TODO: Other stuff which needs propagation?
            WordItem word = (WordItem) phrase.getItem(0);
            word.setWeight(phrase.getWeight());
            return word;
        } else {
            return phrase;
        }
    }

    // TODO: The segmenting stuff is a mess now, this will fix it:
    // - Make Segmenter a class which is instantiated per parsing
    // - Make the instance know the language, etc and do all dispatching
    // internally
    // -JSB
    // TODO: Use segmenting for forced phrase searches?
    protected Item segment(Token token) {
        String normalizedToken = normalize(token.toString());

        if (token.isSpecial()) {
            final WordItem w = new WordItem(token.toString(), true, token.substring);
            w.setWords(false);
            w.setFromSpecialToken(true);
            return w;
        }

        if (language == Language.UNKNOWN) {
            return new WordItem(normalizedToken, true, token.substring);
        }


        Segmenter segmenter = environment.getLinguistics().getSegmenter();
        List<String> segments = segmenter.segment(normalizedToken, language);
        if (segments.size() == 0) {
            return null;
        }
        if (segments.size() == 1) {
            return new WordItem(segments.get(0), "", true, token.substring);
        }

        CompositeItem composite = new PhraseSegmentItem(token.toString(), normalizedToken, true, false, token.substring);
        int n = 0;
        for (String segment : segments) {
            WordItem w = new WordItem(segment, "", true, token.substring);
            w.setFromSegmented(true);
            w.setSegmentIndex(n++);
            w.setStemmed(false);
            composite.addItem(w);
        }
        composite.lock();
        return composite;
    }

}
