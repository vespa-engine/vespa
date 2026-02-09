// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.querytransform.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yahoo.component.chain.Chain;
import com.yahoo.language.Language;
import com.yahoo.language.Linguistics;
import com.yahoo.language.process.Transformer;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.prelude.Index;
import com.yahoo.prelude.SearchDefinition;
import com.yahoo.prelude.query.PhraseSegmentItem;
import com.yahoo.prelude.query.Substring;
import com.yahoo.prelude.query.WordAlternativesItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.IndexModel;
import com.yahoo.prelude.querytransform.NormalizingSearcher;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.test.QueryTestCase;
import com.yahoo.search.yql.MinimalQueryInserter;

import org.junit.jupiter.api.Test;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author bratseth
 */
public class NormalizingSearcherTestCase {

    private static final Linguistics linguistics = new SimpleLinguistics();

    @Test
    void testNoNormalizingNecssary() {
        Query query = new Query("/search?query=bilen&search=cluster1&restrict=type1");
        createExecution().search(query);
        assertEquals("WEAKAND(100) bilen", query.getModel().getQueryTree().getRoot().toString());
    }

    @Test
    void testAttributeQuery() {
        Query query = new Query("/search?query=attribute:" + enc("b\u00e9yonc\u00e8 b\u00e9yonc\u00e8") + "&search=cluster1&restrict=type1");
        createExecution().search(query);
        assertEquals("WEAKAND(100) attribute:b\u00e9yonc\u00e8 beyonce", query.getModel().getQueryTree().getRoot().toString());
    }

    @Test
    void testOneTermNormalizing() {
        Query query = new Query("/search?query=b\u00e9yonc\u00e8&search=cluster1&restrict=type1");
        createExecution().search(query);
        assertEquals("WEAKAND(100) beyonce", query.getModel().getQueryTree().getRoot().toString());
    }

    @Test
    void testOneTermNoNormalizingDifferentSearchDef() {
        Query query = new Query("/search?query=b\u00e9yonc\u00e8&search=cluster1&restrict=type2");
        createExecution().search(query);
        assertEquals("WEAKAND(100) béyoncè", query.getModel().getQueryTree().getRoot().toString());
    }

    @Test
    void testTwoTermQuery() throws UnsupportedEncodingException {
        Query query = new Query("/search?query=" + enc("b\u00e9yonc\u00e8 beyonc\u00e9") + "&search=cluster1&restrict=type1");
        createExecution().search(query);
        assertEquals("WEAKAND(100) beyonce beyonce", query.getModel().getQueryTree().getRoot().toString());
    }

    private String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    @Test
    void testPhraseQuery() {
        Query query = new Query("/search?query=" + enc("\"b\u00e9yonc\u00e8 beyonc\u00e9\"") + "&search=cluster1&restrict=type1");
        query.getTrace().setLevel(2);
        createExecution().search(query);
        assertEquals("WEAKAND(100) \"beyonce beyonce\"", query.getModel().getQueryTree().getRoot().toString());
    }

    @Test
    void testLiteralBoost() {
        Query query = new Query("/search?query=nop&search=cluster1&restrict=type1");
        List<WordAlternativesItem.Alternative> terms = new ArrayList<>();
        Substring origin = new Substring(0, 5, "h\u00F4tels");
        terms.add(new WordAlternativesItem.Alternative("h\u00F4tels", 1.0d));
        terms.add(new WordAlternativesItem.Alternative("h\u00F4tel", 0.7d));
        query.getModel().getQueryTree().setRoot(new WordAlternativesItem("default", true, origin, terms));
        createExecution().search(query);
        WordAlternativesItem w = (WordAlternativesItem) query.getModel().getQueryTree().getRoot();
        assertEquals(4, w.getAlternatives().size());
        boolean foundHotel = false;
        for (WordAlternativesItem.Alternative a : w.getAlternatives()) {
            if ("hotel".equals(a.word)) {
                foundHotel = true;
                assertEquals(.7d * .7d, a.exactness, 1e-15);
            }
        }
        assertTrue(foundHotel, "Did not find the expected normalized form \"hotel\".");
    }


    @Test
    void testPhraseSegmentNormalization() {
        Query query = new Query("/search?query=&search=cluster1&restrict=type1");
        PhraseSegmentItem phraseSegment = new PhraseSegmentItem("default", false, false);
        phraseSegment.addItem(new WordItem("net"));
        query.getModel().getQueryTree().setRoot(phraseSegment);
        assertEquals("'net'", query.getModel().getQueryTree().getRoot().toString());
        createExecution().search(query);
        assertEquals("'net'", query.getModel().getQueryTree().getRoot().toString());
    }

    private Execution createExecution() {
        return new Execution(new NormalizingSearcher(linguistics),
                             Execution.Context.createContextStub(createIndexFacts(), linguistics));
    }

    private IndexFacts createIndexFacts() {
        Map<String, List<String>> clusters = new LinkedHashMap<>();
        clusters.put("cluster1", List.of("type1", "type2", "type3"));
        clusters.put("cluster2", List.of("type4", "type5"));
        Collection<SearchDefinition> searchDefs = List.of(
                createSearchDefinitionWithFields("type1", true),
                createSearchDefinitionWithFields("type2", false),
                new SearchDefinition("type3"),
                new SearchDefinition("type4"),
                new SearchDefinition("type5"));
        return new IndexFacts(new IndexModel(clusters, searchDefs));
    }

    private SearchDefinition createSearchDefinitionWithFields(String name, boolean normalize) {
        SearchDefinition type = new SearchDefinition(name);

        Index defaultIndex = new Index("default");
        defaultIndex.setNormalize(normalize);
        type.addIndex(defaultIndex);

        Index absoluteIndex = new Index("absolute");
        absoluteIndex.setNormalize(normalize);
        type.addIndex(absoluteIndex);

        Index normalizercheckIndex = new Index("normalizercheck");
        normalizercheckIndex.setNormalize(normalize);
        type.addIndex(normalizercheckIndex);

        Index attributeIndex = new Index("attribute");
        attributeIndex.setAttribute(true);
        type.addIndex(attributeIndex);

        return type;
    }

    // --- Per-clause language tests ---

    private Result searchWithNormalizing(Linguistics linguistics, IndexModel indexModel, Query query) {
        return new Execution(new Chain<>(new MinimalQueryInserter(linguistics), new NormalizingSearcher(linguistics)),
                             Execution.Context.createContextStub(new IndexFacts(indexModel), linguistics)).search(query);
    }

    private static IndexModel createNormalizingIndexModel() {
        var schema = new SearchDefinition("test");
        var index = new Index("default");
        index.setNormalize(true);
        schema.addIndex(index);
        return new IndexModel(schema);
    }

    @Test
    void testPerClauseLanguagePassedToAccentDrop() {
        var recording = new LanguageRecordingLinguistics();
        var indexModel = createNormalizingIndexModel();
        var yql = "select * from sources * where " +
                  "({language: 'fr', grammar: 'all'}userInput(@query)) or " +
                  "({language: 'en', grammar: 'all'}userInput(@query))";
        var query = new Query("?yql=" + QueryTestCase.httpEncode(yql) +
                              "&query=" + QueryTestCase.httpEncode("b\u00e9yonc\u00e8"));
        var result = searchWithNormalizing(recording, indexModel, query);
        if (result.hits().getError() != null)
            throw new RuntimeException(result.hits().getError().toString());
        // The word should have been normalized on both branches
        assertEquals(Language.FRENCH, recording.recordedLanguages.get("b\u00e9yonc\u00e8-1"),
                "First branch should normalize with FRENCH");
        assertEquals(Language.ENGLISH, recording.recordedLanguages.get("b\u00e9yonc\u00e8-2"),
                "Second branch should normalize with ENGLISH");
    }

    @Test
    void testAccentNormalizationViaYqlPipeline() {
        var indexModel = createNormalizingIndexModel();
        var yql = "select * from sources * where ({grammar: 'all'}userInput(@query))";
        var query = new Query("?yql=" + QueryTestCase.httpEncode(yql) +
                              "&query=" + QueryTestCase.httpEncode("b\u00e9yonc\u00e8"));
        var result = searchWithNormalizing(linguistics, indexModel, query);
        if (result.hits().getError() != null)
            throw new RuntimeException(result.hits().getError().toString());
        // Accent should be dropped: béyoncè -> beyonce
        assertEquals("default:beyonce", query.getModel().getQueryTree().toString());
    }

    @Test
    void testOneBranchExplicitOneBranchDefault() {
        var recording = new LanguageRecordingLinguistics();
        var indexModel = createNormalizingIndexModel();
        var yql = "select * from sources * where " +
                  "({language: 'fr', grammar: 'all'}userInput(@query)) or " +
                  "({grammar: 'all'}userInput(@query))";
        var query = new Query("?yql=" + QueryTestCase.httpEncode(yql) +
                              "&query=" + QueryTestCase.httpEncode("caf\u00e9"));
        var result = searchWithNormalizing(recording, indexModel, query);
        if (result.hits().getError() != null)
            throw new RuntimeException(result.hits().getError().toString());
        // First branch: explicit French, second branch: default (French from model, set by first userInput)
        assertEquals(Language.FRENCH, recording.recordedLanguages.get("caf\u00e9-1"),
                "First branch should normalize with FRENCH");
        assertEquals(Language.FRENCH, recording.recordedLanguages.get("caf\u00e9-2"),
                "Second branch should normalize with model language (FRENCH from first userInput)");
    }

    @Test
    void testAccentNormalizedOnBothBranchesOfTwoLanguageQuery() {
        var indexModel = createNormalizingIndexModel();
        var yql = "select * from sources * where " +
                  "({language: 'fr', grammar: 'all'}userInput(@query)) or " +
                  "({language: 'en', grammar: 'all'}userInput(@query))";
        var query = new Query("?yql=" + QueryTestCase.httpEncode(yql) +
                              "&query=" + QueryTestCase.httpEncode("caf\u00e9"));
        var result = searchWithNormalizing(linguistics, indexModel, query);
        if (result.hits().getError() != null)
            throw new RuntimeException(result.hits().getError().toString());
        // Both branches should have accents dropped
        assertEquals("OR default:cafe default:cafe", query.getModel().getQueryTree().toString());
    }

    @Test
    void testNoAnnotationUsesDefaultLanguage() {
        var recording = new LanguageRecordingLinguistics();
        var indexModel = createNormalizingIndexModel();
        var yql = "select * from sources * where ({grammar: 'all'}userInput(@query))";
        var query = new Query("?yql=" + QueryTestCase.httpEncode(yql) +
                              "&query=" + QueryTestCase.httpEncode("b\u00e9yonc\u00e8"));
        var result = searchWithNormalizing(recording, indexModel, query);
        if (result.hits().getError() != null)
            throw new RuntimeException(result.hits().getError().toString());
        // No language annotation: should use default (ENGLISH)
        assertEquals(Language.ENGLISH, recording.recordedLanguages.get("b\u00e9yonc\u00e8-1"),
                "Should normalize with default language ENGLISH");
    }

    private static class LanguageRecordingLinguistics extends SimpleLinguistics {

        final Map<String, Language> recordedLanguages = new LinkedHashMap<>();
        private int callCount = 0;

        @Override
        public Transformer getTransformer() {
            var delegate = super.getTransformer();
            return (input, language) -> {
                callCount++;
                recordedLanguages.put(input + "-" + callCount, language);
                return delegate.accentDrop(input, language);
            };
        }
    }

}
