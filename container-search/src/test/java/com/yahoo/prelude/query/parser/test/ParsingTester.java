// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.parser.test;

import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.language.Language;
import com.yahoo.language.Linguistics;
import com.yahoo.language.simple.SimpleDetector;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.IndexModel;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.NullItem;
import com.yahoo.prelude.query.parser.SpecialTokenRegistry;
import com.yahoo.prelude.query.parser.SpecialTokens;
import com.yahoo.search.Query;
import com.yahoo.search.config.IndexInfoConfig;
import com.yahoo.search.query.parser.Parsable;
import com.yahoo.search.query.parser.Parser;
import com.yahoo.search.query.parser.ParserEnvironment;
import com.yahoo.search.query.parser.ParserFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * A utility for writing parser tests
 *
 * @author bratseth
 */
public class ParsingTester {

    private static final Linguistics linguistics = new SimpleLinguistics();
    private IndexFacts indexFacts;
    private SpecialTokenRegistry tokenRegistry;

    public ParsingTester() {
        this(createIndexFacts(), createSpecialTokens());
    }

    public ParsingTester(SpecialTokens specialTokens) {
        this(createIndexFacts(), specialTokens);
    }

    public ParsingTester(IndexFacts indexFacts) {
        this(indexFacts, createSpecialTokens());
    }

    public ParsingTester(IndexFacts indexFacts, SpecialTokens specialTokens) {
        indexFacts.freeze();
        specialTokens.freeze();

        this.indexFacts = indexFacts;
        tokenRegistry = new SpecialTokenRegistry();
        tokenRegistry.addSpecialTokens(specialTokens);
    }

    /**
     * Returns an unfrozen version of the IndexFacts this will use.
     * This can be used to add new indexes and passing the resulting IndexFacts to the constructor of this.
     */
    public static IndexFacts createIndexFacts() {
        String indexInfoConfigID = "file:src/test/java/com/yahoo/prelude/query/parser/test/parseindexinfo.cfg";
        ConfigGetter<IndexInfoConfig> getter = new ConfigGetter<>(IndexInfoConfig.class);
        IndexInfoConfig config = getter.getConfig(indexInfoConfigID);
        return new IndexFacts(new IndexModel(config, (QrSearchersConfig)null));
    }

    /**
     * Returns an unfrozen version of the special tokens this will use.
     * This can be used to add new tokens and passing the resulting special tokens to the constructor of this.
     */
    public static SpecialTokens createSpecialTokens() {
        SpecialTokens tokens = new SpecialTokens("default");
        tokens.addSpecialToken("c++", null);
        tokens.addSpecialToken(".net", "dotnet");
        tokens.addSpecialToken("tcp/ip", null);
        tokens.addSpecialToken("c#", null);
        tokens.addSpecialToken("special-token-fs","firstsecond");
        return tokens;
    }

    /**
     * Asserts that the canonical representation of the second string when parsed
     * is the first string
     *
     * @return the produced root
     */
    public Item assertParsed(String parsed, String toParse, Query.Type mode) {
        return assertParsed(parsed, toParse, null, mode, new SimpleDetector().detect(toParse, null).getLanguage(),
                            new SimpleLinguistics());
    }

    /**
     * Asserts that the canonical representation of the second string when parsed
     * is the first string
     *
     * @return the produced root
     */
    public Item assertParsed(String parsed, String toParse, String filter, Query.Type mode) {
        return assertParsed(parsed, toParse, filter, mode, new SimpleDetector().detect(toParse,null).getLanguage());
    }

    public Item assertParsed(String parsed, String toParse, String filter, Query.Type mode, Language language) {
        return assertParsed(parsed, toParse, filter, mode, language, linguistics);
    }

    /**
     * Asserts that the canonical representation of the second string when parsed
     * is the first string
     *
     * @return the produced root
     */
    public Item assertParsed(String parsed, String toParse, String filter, Query.Type mode,
                              Language language, Linguistics linguistics) {
        Item root = parseQuery(toParse, filter, language, mode, linguistics);
        if (parsed == null) {
            assertTrue("root should be null, but was " + root, root == null);
        } else {
            assertNotNull("Got null from parsing " + toParse, root);
            assertEquals("Parse of '" + toParse + "'", parsed, root.toString());
        }
        return root;
    }

    public Item parseQuery(String query, String filter, Language language, Query.Type type, Linguistics linguistics) {
        Parser parser = ParserFactory.newInstance(type, new ParserEnvironment()
                .setIndexFacts(indexFacts)
                .setLinguistics(linguistics)
                .setSpecialTokens(tokenRegistry.getSpecialTokens("default")));
        Item root = parser.parse(new Parsable().setQuery(query).setFilter(filter).setLanguage(language)).getRoot();
        if (root instanceof NullItem) {
            return null;
        }
        return root;
    }

}
