// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

import com.yahoo.language.process.LinguisticsParameters;
import com.yahoo.language.process.Token;
import com.yahoo.language.process.Tokenizer;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.language.simple.SimpleToken;
import com.yahoo.language.simple.SimpleTokenizer;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.Query;
import com.yahoo.search.query.QueryTree;
import com.yahoo.search.query.parser.Parsable;
import com.yahoo.search.query.parser.ParserEnvironment;
import com.yahoo.search.schema.SchemaInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * @author bratseth
 */
public class LinguisticsParserTester {

    private final MockTokenizer mockTokenizer;
    private final YqlParser parser;

    public LinguisticsParserTester() {
        this(null, null);
    }

    public LinguisticsParserTester(SchemaInfo schemaInfo, IndexFacts indexFacts) {
        this.mockTokenizer = new MockTokenizer();
        var environment = new ParserEnvironment();
        if (schemaInfo != null)
            environment.setSchemaInfo(schemaInfo);
        if (indexFacts != null)
            environment.setIndexFacts(indexFacts);
        environment.setLinguistics(new MockLinguistics(mockTokenizer));
        this.parser = new YqlParser(environment);
    }

    public MockTokenizer tokenizer() { return mockTokenizer; }

    public QueryTree parse(String yqlQuery) {
        return parser.parse(new Parsable().setQuery(yqlQuery));
    }

    public QueryTree parse(String yqlQuery, String key, String value) {
        Query userQuery = new Query();
        userQuery.properties().set(key, value);
        parser.setUserQuery(userQuery);
        return parse(yqlQuery);
    }

    public QueryTree assertParsed(String expected, String query) {
        var root = parse(query);
        assertEquals(expected, root.toString());
        return root;
    }

    public void assertCompositeOfWords(Item root, Class<? extends CompositeItem> expectedType,
                                               String expectedField, int expectedChildren) {
        assertInstanceOf(expectedType, root);
        CompositeItem composite = (CompositeItem) root;
        assertEquals(expectedChildren, composite.getItemCount());
        for (int i = 0; i < composite.getItemCount(); i++) {
            assertInstanceOf(WordItem.class, composite.getItem(i));
            assertEquals(expectedField, ((WordItem) composite.getItem(i)).getIndexName());
        }
    }

    public WordItem getFirstWord(Item root) {
        if (root instanceof CompositeItem composite) {
            assertInstanceOf(WordItem.class, composite.getItem(0));
            return (WordItem) composite.getItem(0);
        }
        assertInstanceOf(WordItem.class, root);
        return (WordItem)root;
    }

    public static class MockTokenizer extends SimpleTokenizer {

        /** A map from input text to output tokens per profile. */
        Map<String, Map<String, List<Token>>> tokensPerProfile = new HashMap<>();

        /** Adds a tokenize result that will override the fallback SimpleTokenizer behavior. */
        public MockTokenizer putTokens(String profile, String input, String ... outputTokens) {
            return putTokens(profile, input, asTokens(outputTokens));
        }

        /** Adds a tokenize result that will override the fallback SimpleTokenizer behavior. */
        public MockTokenizer putTokens(String profile, String input, Token ... outputTokens) {
            return putTokens(profile, input, List.of(outputTokens));
        }

        /** Adds a tokenize result that will override the fallback SimpleTokenizer behavior. */
        public MockTokenizer putTokens(String profile, String input, List<Token> outputTokens) {
            var profileTokens = tokensPerProfile.computeIfAbsent(profile, key -> new HashMap<>());
            profileTokens.put(input, outputTokens);
            return this;
        }

        private List<Token> asTokens(String ... tokenStrings) {
            List<Token> tokens = new ArrayList<>();
            for (String tokenString : tokenStrings)
                tokens.add(SimpleToken.fromStems(tokenString, List.of(tokenString)));
            return tokens;
        }

        @Override
        public Iterable<Token> tokenize(String input, LinguisticsParameters parameters) {
            var profileTokens = tokensPerProfile.get(parameters.profile());
            if (profileTokens != null) {
                var output = profileTokens.get(input);
                if (output != null)
                    return output;
            }
            return super.tokenize(input, parameters);
        }

    }

    public static class MockLinguistics extends SimpleLinguistics {

        private final Tokenizer tokenizer;

        public MockLinguistics(Tokenizer tokenizer) {
            this.tokenizer = tokenizer;
        }

        @Override
        public Tokenizer getTokenizer() { return tokenizer; }

    }

}
