// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

import static org.junit.jupiter.api.Assertions.*;

import com.yahoo.language.Language;
import com.yahoo.prelude.Index;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.IndexModel;
import com.yahoo.prelude.SearchDefinition;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.OrItem;
import com.yahoo.prelude.query.WeakAndItem;
import com.yahoo.prelude.query.WordItem;
import org.apache.http.client.utils.URIBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.yahoo.component.chain.Chain;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;

import java.util.Arrays;

import static com.yahoo.container.protect.Error.INVALID_QUERY_PARAMETER;

/**
 * Tests where you really test YqlParser but need the full Query infrastructure.
 *
 * @author steinar
 */
public class UserInputTestCase {

    private Chain<Searcher> searchChain;
    private Execution.Context context;
    private Execution execution;

    @BeforeEach
    public void setUp() throws Exception {
        searchChain = new Chain<>(new MinimalQueryInserter());
        context = Execution.Context.createContextStub();
        execution = new Execution(searchChain, context);
    }

    @AfterEach
    public void tearDown() {
        searchChain = null;
        context = null;
        execution = null;
    }

    @Test
    public void testNear() {
        URIBuilder builder = searchUri();
        builder.setParameter("yql", "select * from sources * where ({grammar.syntax:'none',grammar.tokenization:'linguistics',grammar.composite:'near'}userInput('Noëlᛁ continuation'))");
        // Further token processing is disabled due to type=linguistics applied by default to all terms
        assertEquals("select * from sources * where default contains near(({stem: false, normalizeCase: false, accentDrop: false, implicitTransforms: false}\"noel\\u16C1\"), ({stem: false, normalizeCase: false, accentDrop: false, implicitTransforms: false}\"continuation\"))",
                     searchAndAssertNoErrors(builder).yqlRepresentation());
    }

    @Test
    public void testNearDistanceAnnotation() {
        URIBuilder builder = searchUri();
        builder.setParameter("yql", "select * from sources * where ({grammar.syntax:'none',grammar.tokenization:'linguistics',grammar.composite:'near',distance:3}userInput('a b'))");
        Query near = searchAndAssertNoErrors(builder);
        assertEquals("select * from sources * where default contains ({distance: 3}near(({stem: false, normalizeCase: false, accentDrop: false, implicitTransforms: false}\"a\"), ({stem: false, normalizeCase: false, accentDrop: false, implicitTransforms: false}\"b\")))",
                     near.yqlRepresentation());

        builder.setParameter("yql", "select * from sources * where ({grammar.syntax:'none',grammar.tokenization:'linguistics',grammar.composite:'oNear',distance:4}userInput('a b'))");
        Query oNear = searchAndAssertNoErrors(builder);
        assertEquals("select * from sources * where default contains ({distance: 4}onear(({stem: false, normalizeCase: false, accentDrop: false, implicitTransforms: false}\"a\"), ({stem: false, normalizeCase: false, accentDrop: false, implicitTransforms: false}\"b\")))",
                     oNear.yqlRepresentation());
    }

    @Test
    void testSimpleUserInput() {
        {
            URIBuilder builder = searchUri();
            builder.setParameter("yql", "select * from sources * where userInput(\"nalle\")");
            Query query = searchAndAssertNoErrors(builder);
            assertEquals("select * from sources * where weakAnd(default contains \"nalle\")", query.yqlRepresentation());
        }
        {
            URIBuilder builder = searchUri();
            builder.setParameter("nalle", "bamse");
            builder.setParameter("yql", "select * from sources * where userInput(@nalle)");
            Query query = searchAndAssertNoErrors(builder);
            assertEquals("select * from sources * where weakAnd(default contains \"bamse\")", query.yqlRepresentation());
        }
        {
            URIBuilder builder = searchUri();
            builder.setParameter("nalle", "bamse");
            builder.setParameter("yql", "select * from sources * where userInput(nalle)");
            Query query = new Query(builder.toString());
            Result r = execution.search(query);
            assertNotNull(r.hits().getError());
        }
    }

    @Test
    void testRawUserInput() {
        URIBuilder builder = searchUri();
        builder.setParameter("yql", "select * from sources * where {grammar: \"raw\"}userInput(\"nal le\")");
        Query query = searchAndAssertNoErrors(builder);
        assertEquals("select * from sources * where default contains \"nal le\"", query.yqlRepresentation());
    }

    @Test
    void testUserInputInSameElement() {
        URIBuilder builder = searchUri();
        builder.setParameter("yql", "select * from sources * where myArray contains sameElement({grammar:'all'}userInput('a b'))");
        Query query = searchAndAssertNoErrors(builder);
        assertEquals("select * from sources * where myArray contains sameElement(\"a\" AND \"b\")",
                     query.yqlRepresentation());
    }

    /** weakAnd in SameElement: Not supported, will be stopped by downstream query validation */
    @Test
    void testSameElementUserInputWithWeakAnd() {
        URIBuilder builder = searchUri();
        builder.setParameter("yql",
                             "select * from sources * where a contains 'b' and c contains sameElement(userInput(@query))");
        builder.setParameter("query", "c d");
        Query query = searchAndAssertNoErrors(builder);
        assertEquals("select * from sources * where (a contains \"b\" AND c contains sameElement(weakAnd(\"c\", \"d\")))",
                     query.yqlRepresentation());
    }

    @Test
    void testMustAndShouldUserInput() {
        URIBuilder builder = searchUri();
        builder.setParameter("yql",
                             "select * from sources * where " +
                             "{grammar: 'all'}rank(userInput('must terms'), {grammar: 'any'}userInput('should terms'))"
                            );
        Query query = searchAndAssertNoErrors(builder);
        assertEquals("select * from sources * where rank((default contains \"must\" AND default contains \"terms\"), (default contains \"should\" OR default contains \"terms\"))", query.yqlRepresentation());
    }

    @Test
    void testGrammarDetails() {
        URIBuilder builder = searchUri();
        builder.setParameter("yql",
                             "select * from sources * where " +
                             "{grammar.composite:'or', grammar.tokenization:'linguistics', grammar.syntax:'none'}userInput('a b -c')"
                            );
        Query query1 = searchAndAssertNoErrors(builder);
        assertEquals("select * from sources * where (default contains ({stem: false, normalizeCase: false, accentDrop: false, implicitTransforms: false}\"a\") OR " +
                                                             "default contains ({stem: false, normalizeCase: false, accentDrop: false, implicitTransforms: false}\"b\") OR " +
                                                             "default contains ({stem: false, normalizeCase: false, accentDrop: false, implicitTransforms: false}\"c\"))",
                     query1.yqlRepresentation());

        builder.setParameter("yql",
                             "select * from sources * where " +
                             "{grammar.composite:'weakAnd', grammar.tokenization:'internal', grammar.syntax:'web'}userInput('a b -c')"
                            );
        Query query2 = searchAndAssertNoErrors(builder);
        assertEquals("select * from sources * where (weakAnd(default contains \"a\", default contains \"b\")) AND !(default contains \"c\")",
                     query2.yqlRepresentation());
    }

    @Test
    void testSegmentedUserInput() {
        URIBuilder builder = searchUri();
        builder.setParameter("yql",
                "select * from sources * where {grammar: \"segment\"}userInput(\"nal le\")");
        Query query = searchAndAssertNoErrors(builder);
        assertEquals("select * from sources * where default contains ({origin: {original: \"nal le\", offset: 0, length: 6}}phrase(\"nal\", \"le\"))", query.yqlRepresentation());
    }

    @Test
    void testUserInputSettingTargetHits() {
        assertTargetHitsIsPropagatedInUserInput("weakAnd");
        assertTargetHitsIsPropagatedInUserInput("tokenize");
    }

    private void assertTargetHitsIsPropagatedInUserInput(String grammar) {
        URIBuilder builder = searchUri();
        builder.setParameter("yql",
                             "select * from sources * where {grammar: \"" + grammar + "\", targetHits: 17, totalTargetHits: 19, defaultIndex: \"f\"}userInput(\"a test\")");
        Query query = searchAndAssertNoErrors(builder);
        assertEquals("select * from sources * where ({targetHits: 17, totalTargetHits: 19}weakAnd(f contains \"a\", f contains \"test\"))", query.yqlRepresentation());
        WeakAndItem weakAnd = (WeakAndItem)query.getModel().getQueryTree().getRoot();
        assertEquals(17, weakAnd.getTargetHits());
        assertEquals(19, weakAnd.getTotalTargetHits());
    }

    @Test
    void testQuotedSymbol() {
        URIBuilder builder = searchUri();
        builder.setParameter("yql",
                             "select * from sources * where {targetHits: 500}userInput(@query)");
        builder.setParameter("query", "˘͈ᵕ˘͈ meaning in english");
        Query query = searchAndAssertNoErrors(builder);
        assertEquals("select * from sources * where ({targetHits: 500}weakAnd((default contains ({origin: {original: \"\\u02D8\\u0348\\u1D55\\u02D8\\u0348 meaning in english\", offset: 1, length: 2}}\"\\u0348\\u1D17\") AND default contains \"\\u0348\"), default contains \"meaning\", default contains \"in\", default contains \"english\"))",
                     query.yqlRepresentation());
    }

    @Test
    void testSegmentedNoiseUserInput() {
        URIBuilder builder = searchUri();
        builder.setParameter("yql",
                "select * from sources * where {grammar: \"segment\"}userInput(\"^^^^^^^^\")");
        Query query = searchAndAssertNoErrors(builder);
        assertEquals("select * from sources * where default contains \"^^^^^^^^\"", query.yqlRepresentation());
    }

    @Test
    void testAnyParsedUserInput() {
        URIBuilder builder = searchUri();
        builder.setParameter("yql", "select * from sources * where {grammar: \"any\"}userInput('foo bar')");
        Query query = searchAndAssertNoErrors(builder);
        assertEquals("select * from sources * where (default contains \"foo\" OR default contains \"bar\")",
                query.yqlRepresentation());
    }

    @Test
    void testAllParsedUserInput() {
        URIBuilder builder = searchUri();
        builder.setParameter("yql", "select * from sources * where {grammar: \"all\"}userInput('foo bar')");
        Query query = searchAndAssertNoErrors(builder);
        assertEquals("select * from sources * where (default contains \"foo\" AND default contains \"bar\")",
                query.yqlRepresentation());
    }

    @Test
    void testWeakAndParsedUserInput() {
        URIBuilder builder = searchUri();
        builder.setParameter("yql", "select * from sources * where {grammar: \"weakAnd\"}userInput('foo bar')");
        Query query = searchAndAssertNoErrors(builder);
        assertEquals("select * from sources * where weakAnd(default contains \"foo\", default contains \"bar\")",
                query.yqlRepresentation());
    }

    @Test
    void testIllegalGrammar() {
        URIBuilder builder = searchUri();
        builder.setParameter("yql", "select * from sources * where {grammar: \"nonesuch\"}userInput('foo bar')");
        Query query = new Query(builder.toString());
        Result r = execution.search(query);
        assertNotNull(r.hits().getError());
        assertEquals("Could not create query from YQL: No query type 'nonesuch'",
                r.hits().getError().getDetailedMessage());
    }

    @Test
    void testCustomDefaultIndexUserInput() {
        URIBuilder builder = searchUri();
        builder.setParameter("yql",
                "select * from sources * where {defaultIndex: \"glompf\"}userInput(\"nalle\")");
        Query query = searchAndAssertNoErrors(builder);
        assertEquals("select * from sources * where weakAnd(glompf contains \"nalle\")", query.yqlRepresentation());
    }

    @Test
    void testNegativeUserInput() {
        URIBuilder builder = searchUri();
        builder.setParameter("yql",
                             "select * from sources * where a contains 'b' and !({grammar:'all',defaultIndex:'e'}userInput(@query))");
        builder.setParameter("query", "c d");
        Query query = searchAndAssertNoErrors(builder);
        assertEquals("select * from sources * where (a contains \"b\") AND !((e contains \"c\" AND e contains \"d\"))",
                     query.yqlRepresentation());
    }

    @Test
    void testCustomUserInputWithTwoDefaultIndexes() {
        URIBuilder builder = searchUri();
        builder.setParameter("query", "foo");
        builder.setParameter("yql",
                             "select * from sources * where ({defaultIndex: 'fields1'}userInput(@query)) or ({defaultIndex: 'field2'}userInput(@query))");
        Query query = searchAndAssertNoErrors(builder);
        assertEquals("select * from sources * where (weakAnd(fields1 contains \"foo\") OR weakAnd(field2 contains \"foo\"))", query.yqlRepresentation());
    }

    @Test
    void testAnnotatedUserInputStemming() {
        URIBuilder builder = searchUri();
        builder.setParameter("yql",
                "select * from sources * where {stem: false}userInput(\"nalle\")");
        Query query = searchAndAssertNoErrors(builder);
        assertEquals(
                "select * from sources * where weakAnd(default contains ({stem: false}\"nalle\"))",
                query.yqlRepresentation());
    }

    @Test
    void testNegativeNumberComparison() {
        URIBuilder builder = searchUri();
        builder.setParameter("myinput", "-5");
        builder.setParameter("yql",
                "select * from ecitem where rank(({defaultIndex:\"myfield\"}(userInput(@myinput))))");
        Query query = searchAndAssertNoErrors(builder);
        assertEquals("select * from ecitem where rank(weakAnd(myfield = (-5)))", query.yqlRepresentation());
        assertEquals("RANK (WEAKAND myfield:-5)", query.getModel().getQueryTree().getRoot().toString());
    }

    @Test
    void testAnnotatedUserInputUnrankedTerms() {
        URIBuilder builder = searchUri();
        builder.setParameter("yql",
                "select * from sources * where {ranked: false}userInput(\"nalle\")");
        Query query = searchAndAssertNoErrors(builder);
        assertEquals(
                "select * from sources * where weakAnd(default contains ({ranked: false}\"nalle\"))",
                query.yqlRepresentation());
    }

    @Test
    void testAnnotatedUserInputFiltersTerms() {
        URIBuilder builder = searchUri();
        builder.setParameter("yql",
                "select * from sources * where {filter: true}userInput(\"nalle\")");
        Query query = searchAndAssertNoErrors(builder);
        assertEquals(
                "select * from sources * where weakAnd(default contains ({filter: true}\"nalle\"))",
                query.yqlRepresentation());
    }

    @Test
    void testAnnotatedUserInputCaseNormalization() {
        URIBuilder builder = searchUri();
        builder.setParameter(
                "yql",
                "select * from sources * where {normalizeCase: false}userInput(\"nalle\")");
        Query query = searchAndAssertNoErrors(builder);
        assertEquals(
                "select * from sources * where weakAnd(default contains ({normalizeCase: false}\"nalle\"))",
                query.yqlRepresentation());
    }

    @Test
    void testAnnotatedUserInputAccentRemoval() {
        URIBuilder builder = searchUri();
        builder.setParameter("yql",
                "select * from sources * where {accentDrop: false}userInput(\"nalle\")");
        Query query = searchAndAssertNoErrors(builder);
        assertEquals(
                "select * from sources * where weakAnd(default contains ({accentDrop: false}\"nalle\"))",
                query.yqlRepresentation());
    }

    @Test
    void testAnnotatedUserInputPositionData() {
        URIBuilder builder = searchUri();
        builder.setParameter("yql",
                "select * from sources * where {usePositionData: false}userInput(\"nalle\")");
        Query query = searchAndAssertNoErrors(builder);
        assertEquals(
                "select * from sources * where weakAnd(default contains ({usePositionData: false}\"nalle\"))",
                query.yqlRepresentation());
    }

    @Test
    void testQueryPropertiesAsStringArguments() {
        URIBuilder builder = searchUri();
        builder.setParameter("nalle", "bamse");
        builder.setParameter("meta", "syntactic");
        builder.setParameter("yql",
                "select * from sources * where foo contains @nalle and foo contains phrase(@nalle, @meta, @nalle)");
        Query query = searchAndAssertNoErrors(builder);
        assertEquals("select * from sources * where (foo contains \"bamse\" AND foo contains phrase(\"bamse\", \"syntactic\", \"bamse\"))", query.yqlRepresentation());
    }

    @Test
    void testReferenceInComparison() {
        URIBuilder builder = searchUri();
        builder.setParameter("varref", "1980");
        builder.setParameter("yql", "select * from sources * where year > @varref");
        Query query = searchAndAssertNoErrors(builder);
        assertEquals("select * from sources * where year > 1980", query.yqlRepresentation());
    }

    @Test
    void testReferenceInContinuation() {
        URIBuilder builder = searchUri();
        builder.setParameter("continuation", "BCBCBCBEBG");
        builder.setParameter("yql",
                "select * from sources * where myfield contains 'token'" +
                        "| {'continuations':[@continuation, 'BCBKCBACBKCCK'] }all(group(f) each(output(count())))");
        Query query = searchAndAssertNoErrors(builder);
        assertEquals("select * from sources * where myfield contains \"token\" | { continuations:['BCBCBCBEBG', 'BCBKCBACBKCCK'] }all(group(f) each(output(count())))", query.yqlRepresentation());
    }

    @Test
    void testReferenceInEquiv() {
        URIBuilder builder = searchUri();
        builder.setParameter("term", "A");
        builder.setParameter("yql",
                "select foo from bar where fieldName contains equiv(@term,'B')");
        Query query = searchAndAssertNoErrors(builder);
        assertEquals("select foo from bar where fieldName contains equiv(\"A\", \"B\")", query.yqlRepresentation());
    }

    private Query searchAndAssertNoErrors(URIBuilder builder) {
        Query query = new Query(builder.toString());
        Result r = execution.search(query);
        assertNull(r.hits().getError(), stackTraceIfAny(r));
        return query;
    }

    private String stackTraceIfAny(Result r) {
        if (r.hits().getError() == null) return "";
        if (r.hits().getError().getCause() == null) return "";
        return Arrays.toString(r.hits().getError().getCause().getStackTrace());
    }

    private URIBuilder searchUri() {
        URIBuilder builder = new URIBuilder();
        builder.setPath("search/");
        return builder;
    }

    @Test
    void testEmptyUserInput() {
        URIBuilder builder = searchUri();
        builder.setParameter("yql", "select * from sources * where userInput(\"\")");
        assertQueryFails(builder);
    }

    @Test
    void testEmptyUserInputFromQueryProperty() {
        URIBuilder builder = searchUri();
        builder.setParameter("foo", "");
        builder.setParameter("yql", "select * from sources * where userInput(@foo)");
        assertQueryFails(builder);
    }

    @Test
    void testEmptyQueryProperty() {
        URIBuilder builder = searchUri();
        builder.setParameter("foo", "");
        builder.setParameter("yql", "select * from sources * where bar contains \"a\" and nonEmpty(foo contains @foo)");
        assertQueryFails(builder);
    }

    @Test
    void testEmptyQueryPropertyInsideExpression() {
        URIBuilder builder = searchUri();
        builder.setParameter("foo", "");
        builder.setParameter("yql",
                "select * from sources * where bar contains \"a\" and nonEmpty(bar contains \"bar\" and foo contains @foo)");
        assertQueryFails(builder);
    }

    @Test
    void testCompositeWithoutArguments() {
        URIBuilder builder = searchUri();
        builder.setParameter("yql", "select * from sources * where bar contains \"a\" and foo contains phrase()");
        searchAndAssertNoErrors(builder);
        builder = searchUri();
        builder.setParameter("yql", "select * from sources * where bar contains \"a\" and nonEmpty(foo contains phrase())");
        assertQueryFails(builder);
    }

    @Test
    void testAnnoyingPlacementOfNonEmpty() {
        URIBuilder builder = searchUri();
        builder.setParameter("yql",
                "select * from sources * where bar contains \"a\" and foo contains nonEmpty(phrase(\"a\", \"b\"))");
        assertQueryFails(builder);
    }

    private void assertQueryFails(URIBuilder builder) {
        Result r = execution.search(new Query(builder.toString()));
        assertEquals(INVALID_QUERY_PARAMETER.code, r.hits().getError().getCode());
    }

    @Test
    void testAllowEmptyUserInput() {
        URIBuilder builder = searchUri();
        builder.setParameter("foo", "");
        builder.setParameter("yql", "select * from sources * where [{allowEmpty: true}]userInput(@foo)");
        searchAndAssertNoErrors(builder);
    }

    @Test
    void testAllowEmptyNullFromQueryParsing() {
        URIBuilder builder = searchUri();
        builder.setParameter("foo", ",,,,,,,,");
        builder.setParameter("yql", "select * from sources * where [{allowEmpty: true}]userInput(@foo)");
        searchAndAssertNoErrors(builder);
    }

    @Test
    void testDisallowEmptyNullFromQueryParsing() {
        URIBuilder builder = searchUri();
        builder.setParameter("foo", ",,,,,,,,");
        builder.setParameter("yql", "select * from sources * where userInput(@foo)");
        assertQueryFails(builder);
    }

    @Test
    void testUserInputWithEmptyRangeStart() {
        URIBuilder builder = searchUri();
        builder.setParameter("wql", "[;boom]");
        builder.setParameter("yql", "select * from sources * where ([{\"defaultIndex\": \"text_field\",\"grammar\": \"any\"}]userInput(@wql))");
        Query query = searchAndAssertNoErrors(builder);
        assertEquals("select * from sources * where text_field contains \"boom\"", query.yqlRepresentation());
    }

    @Test
    void testUserInputWithPhraseSegmentingIndex() {
        execution = new Execution(searchChain, Execution.Context.createContextStub(createIndexFacts(true)));
        URIBuilder builder = searchUri();
        builder.setParameter("wql", "foo&bar");
        builder.setParameter("yql", "select * from sources * where ([{\"defaultIndex\": \"text_field\",\"grammar\": \"any\"}]userInput(@wql))");
        Query query = searchAndAssertNoErrors(builder);
        assertEquals("select * from sources * where text_field contains phrase(\"foo\", \"bar\")", query.yqlRepresentation());
    }

    @Test
    void testUserInputWithNonPhraseSegmentingIndex() {
        execution = new Execution(searchChain, Execution.Context.createContextStub(createIndexFacts(false)));
        URIBuilder builder = searchUri();
        builder.setParameter("wql", "foo&bar");
        builder.setParameter("yql", "select * from sources * where ([{\"defaultIndex\": \"text_field\",\"grammar\": \"any\"}]userInput(@wql))");
        Query query = searchAndAssertNoErrors(builder);
        assertEquals("select * from sources * where (text_field contains \"foo\" AND text_field contains \"bar\")", query.yqlRepresentation());
    }

    @Test
    void testYqlRepresentationShowsLanguagePerClause() {
        URIBuilder builder = searchUri();
        builder.setParameter("query", "hello");
        builder.setParameter("yql",
                "select * from sources * where " +
                "({language: 'fr'}userInput(@query)) or ({language: 'en'}userInput(@query))");
        Query query = searchAndAssertNoErrors(builder);
        String yql = query.yqlRepresentation();
        assertTrue(yql.contains("language: \"fr\""),
                "yqlRepresentation should contain French language annotation: " + yql);
        assertTrue(yql.contains("language: \"en\""),
                "yqlRepresentation should contain English language annotation: " + yql);
    }

    @Test
    void testParsingLanguageReflectsExplicitEnglishAnnotation() {
        URIBuilder builder = searchUri();
        builder.setParameter("query", "hello");
        builder.setParameter("yql",
                "select * from sources * where ({language: 'en'}userInput(@query))");
        Query query = searchAndAssertNoErrors(builder);
        // Explicit English annotation: getParsingLanguage should return ENGLISH
        assertEquals(Language.ENGLISH, query.getModel().getParsingLanguage());
    }

    private IndexFacts createIndexFacts(boolean phraseSegmenting) {
        SearchDefinition sd = new SearchDefinition("sources");
        Index test = new Index("text_field");
        test.setPhraseSegmenting(phraseSegmenting);
        sd.addIndex(test);
        return new IndexFacts(new IndexModel(sd));
    }

    @Test
    void testMultiWordFrenchUserInputSetsLanguageOnAllChildren() {
        URIBuilder builder = searchUri();
        builder.setParameter("query", "machine learning");
        builder.setParameter("yql",
                "select * from sources * where ({language: 'fr', grammar: 'all'}userInput(@query))");
        Query query = searchAndAssertNoErrors(builder);
        Item root = query.getModel().getQueryTree().getRoot();
        // With grammar:all and multi-word, root should be an AND with children
        assertInstanceOf(CompositeItem.class, root, "Expected composite for multi-word input");
        CompositeItem composite = (CompositeItem) root;
        assertTrue(composite.getItemCount() >= 2, "Expected at least 2 children for 'machine learning'");
        for (int i = 0; i < composite.getItemCount(); i++) {
            assertEquals(Language.FRENCH, composite.getItem(i).getLanguage(),
                    "Child " + i + " should have FRENCH language");
        }
        // The composite itself should also have FRENCH
        assertEquals(Language.FRENCH, root.getLanguage(),
                "Root composite should have FRENCH language");
    }

    @Test
    void testQueryLevelLanguageUsedWhenNoPerItemAnnotation() {
        URIBuilder builder = searchUri();
        builder.setParameter("query", "hello");
        builder.setParameter("language", "ja");
        builder.setParameter("yql",
                "select * from sources * where userInput(@query)");
        Query query = searchAndAssertNoErrors(builder);
        // Query-level language is Japanese
        assertEquals(Language.JAPANESE, query.getModel().getLanguage());
    }

    @Test
    void testMultipleUserInputFirstSetsModelLanguage() {
        URIBuilder builder = searchUri();
        builder.setParameter("query", "hello");
        builder.setParameter("yql",
                "select * from sources * where " +
                "({language: 'fr'}userInput(@query)) or ({language: 'en'}userInput(@query))");
        Query query = searchAndAssertNoErrors(builder);
        // The first userInput has {language: 'fr'}, which should set the model's parsing language
        assertEquals(Language.FRENCH, query.getModel().getParsingLanguage());
        // Check the item tree has both languages
        Item root = query.getModel().getQueryTree().getRoot();
        assertInstanceOf(OrItem.class, root);
        OrItem or = (OrItem) root;
        assertEquals(2, or.getItemCount());
        assertEquals(Language.FRENCH, or.getItem(0).getLanguage());
        assertEquals(Language.ENGLISH, or.getItem(1).getLanguage());
    }

    @Test
    void testExplicitEnglishLanguageSetsEnglishOnItems() {
        URIBuilder builder = searchUri();
        builder.setParameter("query", "hello");
        builder.setParameter("yql",
                "select * from sources * where ({language: 'en'}userInput(@query))");
        Query query = searchAndAssertNoErrors(builder);
        Item root = query.getModel().getQueryTree().getRoot();
        // With explicit {language: 'en'}, items should have Language.ENGLISH, not UNKNOWN
        if (root instanceof CompositeItem composite) {
            for (int i = 0; i < composite.getItemCount(); i++) {
                assertEquals(Language.ENGLISH, composite.getItem(i).getLanguage(),
                        "Child item should have ENGLISH language");
            }
        } else {
            assertEquals(Language.ENGLISH, root.getLanguage(),
                    "Root item should have ENGLISH language");
        }
    }

}
