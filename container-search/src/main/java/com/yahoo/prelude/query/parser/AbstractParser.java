// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.parser;

import com.yahoo.language.Language;
import com.yahoo.language.process.Segmenter;
import com.yahoo.prelude.Index;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.query.AndSegmentItem;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.IndexedItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.NullItem;
import com.yahoo.prelude.query.PhraseItem;
import com.yahoo.prelude.query.PhraseSegmentItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.query.QueryTree;
import com.yahoo.search.query.parser.Parsable;
import com.yahoo.search.query.parser.ParserEnvironment;

import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * The Vespa query parser.
 *
 * @author bratseth
 * @author Steinar Knutsen
 */
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

    protected String defaultIndex;

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
    static final class Submodes {

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
        // To avoid this we need to pass an IndexFacts.session down instead - easily done but not without breaking API's
        public void setFromIndex(String indexName, IndexFacts.Session session) {
            if (indexName == null) return;

            reset();
            Index current = session.getIndex(indexName);

            if (current.isUriIndex())
                url = true;
            else if (current.isHostIndex())
                site = true;
        }

        /** Sets default values for all submodes */
        public void reset() {
            url = false;
            site = false;
        }

        /**
         * Returns whether we are in a mode which allows explicit anchoring markers, ^ and $
         */
        public boolean explicitAnchoring() { return site; }

    }

    /**
     * Creates a new instance of this class, storing the given {@link ParserEnvironment} for parse-time access to the
     * environment.
     *
     * @param environment the environment settings to attach to the Parser
     */
    protected AbstractParser(ParserEnvironment environment) {
        this.environment = ParserEnvironment.fromParserEnvironment(environment);
        if (this.environment.getIndexFacts() == null) {
            this.environment.setIndexFacts(new IndexFacts());
        }
    }

    // TODO: Deprecate the unwanted method signatures below
    
    @Override
    public final QueryTree parse(Parsable query) {
        Item root = null;
        if (query != null) {
            root = parse(query.getQuery(),
                         query.getFilter(),
                         query.getExplicitLanguage().orElse(query.getLanguage()),
                         environment.getIndexFacts().newSession(query.getSources(), query.getRestrict()),
                         query.getDefaultIndexName(),
                         query);
        }
        if (root == null) {
            root = new NullItem();
        }
        return new QueryTree(root);
    }

    @Override
    public final Item parse(String queryToParse, String filterToParse, Language parsingLanguage,
                            IndexFacts.Session indexFacts, String defaultIndex) {
        return parse(queryToParse, filterToParse, parsingLanguage, indexFacts, defaultIndex, null);
    }

    private Item parse(String queryToParse, String filterToParse, Language parsingLanguage,
                       IndexFacts.Session indexFacts, String defaultIndex, Parsable parsable) {
        if (queryToParse == null) return null;

        if (defaultIndex != null)
            defaultIndex = indexFacts.getCanonicName(defaultIndex);

        tokenize(queryToParse, defaultIndex, indexFacts, parsingLanguage);

        if (parsingLanguage == null && parsable != null) {
            String detectionText = generateLanguageDetectionTextFrom(tokens, indexFacts, defaultIndex);
            if (detectionText.isEmpty()) // heuristic detection text extraction is fallible
                detectionText = queryToParse;
            parsingLanguage = parsable.getOrDetectLanguage(detectionText);
        }

        setState(parsingLanguage, indexFacts, defaultIndex);
        Item root = parseItems();

        if (filterToParse != null) {
            AnyParser filterParser = new AnyParser(environment);
            if (root == null) {
                root = filterParser.parseFilter(filterToParse, parsingLanguage, indexFacts);
            } else {
                root = filterParser.applyFilter(root, filterToParse, parsingLanguage, indexFacts);
            }
        }
        if (defaultIndex != null)
            assignDefaultIndex(indexFacts.getCanonicName(defaultIndex), root);
        return simplifyPhrases(root);
    }

    /**
     * Do a best-effort attempt at creating a single string for language detection from only the relevant
     * subset of tokens. 
     * The relevant tokens are text tokens which follows names of indexes which are tokenized.
     * 
     * This method does not modify the position of the given token stream.
     */
    private String generateLanguageDetectionTextFrom(TokenPosition tokens, IndexFacts.Session indexFacts, String defaultIndex) {
        StringBuilder detectionText = new StringBuilder();
        int initialPosition = tokens.getPosition();
        while (tokens.hasNext()) { // look for occurrences of text and text:text
            while (!tokens.currentIs(Token.Kind.WORD) && tokens.hasNext()) // skip nonwords
                tokens.next();
            if (!tokens.hasNext()) break;

            String queryText;
            Index index;

            Token word1 = tokens.next();
            if (is(Token.Kind.COLON, tokens.currentNoIgnore())) {
                tokens.next(); // colon
                Token word2 = tokens.next();
                if ( is(Token.Kind.WORD, word2))
                    queryText = word2.image;
                else
                    queryText = "";
                index = indexFacts.getIndex(word1.image);
                if (index.isNull()) { // interpret both as words
                    index = indexFacts.getIndex(defaultIndex);
                    queryText = word1.image + " " + queryText;
                }
            } else if (is(Token.Kind.COLON, tokens.currentNoIgnore()) && is(Token.Kind.QUOTE, tokens.currentNoIgnore(1))) {
                tokens.next(); // colon
                tokens.next(); // quote
                StringBuilder quotedContent = new StringBuilder();
                while (!tokens.currentIs(Token.Kind.QUOTE) && tokens.hasNext()) {
                    Token token = tokens.next();
                    if (is(Token.Kind.WORD, token))
                        quotedContent.append(token.image).append(" ");
                }
                tokens.next();
                queryText = quotedContent.toString();
                index = indexFacts.getIndex(word1.image);
                if (index.isNull()) { // interpret both as words
                    index = indexFacts.getIndex(defaultIndex);
                    queryText = word1.image + " " + queryText;
                }
            } else {
                index = indexFacts.getIndex(defaultIndex);
                queryText = word1.image;
            }

            if (queryText != null && index.hasPlainTokens())
                detectionText.append(queryText).append(" ");
        }
        tokens.setPosition(initialPosition);
        return detectionText.toString();
    }

    /**
     * Assigns the default index to query terms having no default index.
     *
     * This will apply the default index to terms without it added through the filter parameter,
     * where setting defaultIndex into state causes incorrect parsing.
     *
     * @param defaultIndex the default index to assign
     * @param item         the item to check
     */
    private static void assignDefaultIndex(String defaultIndex, Item item) {
        if (defaultIndex == null || item == null) return;

        if (item instanceof IndexedItem indexName) {
            if ("".equals(indexName.getIndexName()))
                indexName.setIndexName(defaultIndex);
        }
        else if (item instanceof CompositeItem) {
            Iterator<Item> items = ((CompositeItem)item).getItemIterator();
            while (items.hasNext())
                assignDefaultIndex(defaultIndex, items.next());
        }
    }

    private boolean is(Token.Kind kind, Token tokenOrNull) {
        if (tokenOrNull == null) return false;
        return kind.equals(tokenOrNull.kind);
    }

    protected abstract Item parseItems();

    /**
     * Unicode normalizes some piece of natural language text. The chosen form
     * is compatibility decomposition, canonical composition (NFKC).
     */
    protected String normalize(String input) {
        if (input == null || input.length() == 0) return input;

        return environment.getLinguistics().getNormalizer().normalize(input);
    }

    protected void setState(Language queryLanguage, IndexFacts.Session indexFacts, String defaultIndex) {
        this.indexFacts = indexFacts;
        this.defaultIndex = defaultIndex;
        this.language = queryLanguage;
        this.submodes.reset();
    }

    /**
     * Tokenizes the given string and initializes tokens with the found tokens.
     *
     * @param query            the string to tokenize
     * @param defaultIndexName the name of the index to use as default
     * @param indexFacts       resolved information about the index we are searching
     * @param language         the language set for this query, or null if none
     */
    protected void tokenize(String query, String defaultIndexName, IndexFacts.Session indexFacts, Language language) {
        Tokenizer tokenizer = new Tokenizer(environment.getLinguistics());
        tokenizer.setSubstringSpecialTokens(language != null && language.isCjk());
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
        } else if (unwashed instanceof CompositeItem composite) {
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
        if (phrase.getItemCount() == 1 && phrase.getItem(0) instanceof WordItem word) {
            // TODO: Other stuff which needs propagation?
            word.setWeight(phrase.getWeight());
            return word;
        } else {
            return phrase;
        }
    }

    /**
     * Segments a token
     *
     * @param indexName the index name which preceeded this token, or null if none
     * @param token the token to segment
     * @param quoted whether this segment is within quoted text
     * @return the resulting item
     */
    // TODO: The segmenting stuff is a mess now, this will fix it:
    // - Make Segmenter a class which is instantiated per parsing
    // - Make the instance know the language, etc and do all dispatching internally
    // -bratseth
    // TODO: Use segmenting for forced phrase searches?
    //
    // Language detection currently depends on tokenization (see generateLanguageDetectionTextFrom), but 
    // - the API's was originally not constructed for that, so a careful and somewhat unsatisfactory dance
    //   must be carried out to make it work
    // - it should really depend on parsing
    // This can be solved by making the segment method language independent by
    // always producing a query item containing the token text and resolve it to a WordItem or
    // SegmentItem after parsing and language detection.
    protected Item segment(String indexName, Token token, boolean quoted) {
        String normalizedToken = normalize(token.toString());

        if (token.isSpecial()) {
            WordItem w = new WordItem(token.toString(), true, token.substring);
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

        CompositeItem composite;
        if (indexFacts.getIndex(indexName).getPhraseSegmenting() || quoted) {
            composite = new PhraseSegmentItem(token.toString(), normalizedToken, true, false, token.substring);
        }
        else {
            composite = new AndSegmentItem(token.toString(), true, false);
        }
        int n = 0;
        WordItem previous = null;
        for (String segment : segments) {
            WordItem w = new WordItem(segment, "", true, token.substring);
            w.setFromSegmented(true);
            w.setSegmentIndex(n++);
            w.setStemmed(false);
            if (previous != null)
                previous.setConnectivity(w, 1.0);
            previous = w;
            composite.addItem(w);
        }
        composite.lock();
        return composite;
    }

}
