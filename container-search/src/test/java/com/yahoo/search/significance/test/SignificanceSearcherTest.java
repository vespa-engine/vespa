// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.significance.test;

import com.yahoo.component.chain.Chain;
import com.yahoo.language.Language;
import com.yahoo.language.Linguistics;
import com.yahoo.language.detect.Detection;
import com.yahoo.language.detect.Detector;
import com.yahoo.language.detect.Hint;
import com.yahoo.language.process.*;
import com.yahoo.language.significance.SignificanceModel;
import com.yahoo.language.significance.SignificanceModelRegistry;
import com.yahoo.language.significance.impl.DefaultSignificanceModelRegistry;
import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.DocumentFrequency;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.schema.DocumentSummary;
import com.yahoo.search.schema.RankProfile;
import com.yahoo.search.schema.Schema;
import com.yahoo.search.schema.SchemaInfo;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.significance.SignificanceSearcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


import static com.yahoo.test.JunitCompat.assertEquals;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Tests significance term in the search chain.
 *
 * @author MariusArhaug
 */
public class SignificanceSearcherTest {
    SignificanceModelRegistry significanceModelRegistry;
    SignificanceSearcher searcher;


    public SignificanceSearcherTest() {
        List<Path> models = new ArrayList<>();
        models.add(Path.of("src/test/java/com/yahoo/search/significance/model/docv1.json"));
        models.add(Path.of("src/test/java/com/yahoo/search/significance/model/docv2.json"));
        var schema = new Schema.Builder("music")
                .add(new DocumentSummary.Builder("default").build())
                .add(new RankProfile.Builder("significance-ranking")
                        .setUseSignificanceModel(true)
                        .build());
        significanceModelRegistry = new DefaultSignificanceModelRegistry(models);
        searcher = new SignificanceSearcher(
                significanceModelRegistry, new SchemaInfo(List.of(schema.build()), List.of()));
    }

    private static class MockLinguistics implements Linguistics {

        private final MockDetector mockDetector;

        MockLinguistics(Language language) {
            this.mockDetector = new MockDetector(language);
        }

        @Override
        public Stemmer getStemmer() {
            throw new UnsupportedOperationException("Not implemented");
        }

        @Override
        public Tokenizer getTokenizer() {
            throw new UnsupportedOperationException("Not implemented");
        }

        @Override
        public Normalizer getNormalizer() {
            throw new UnsupportedOperationException("Not implemented");
        }

        @Override
        public Transformer getTransformer() {
            throw new UnsupportedOperationException("Not implemented");
        }

        @Override
        public Segmenter getSegmenter() {
            throw new UnsupportedOperationException("Not implemented");
        }

        @Override
        public Detector getDetector() {
            return this.mockDetector;
        }

        @Override
        public GramSplitter getGramSplitter() {
            throw new UnsupportedOperationException("Not implemented");
        }

        @Override
        public CharacterClasses getCharacterClasses() {
            throw new UnsupportedOperationException("Not implemented");
        }

        @Override
        public boolean equals(Linguistics other) {
            return false;
        }
    }

    private static class MockDetector implements Detector {

        private Language detectionLanguage;

        MockDetector(Language detectionLanguage) {
            this.detectionLanguage = detectionLanguage;
        }

        @Override
        public Detection detect(byte[] input, int offset, int length, Hint hint) {
            throw new UnsupportedOperationException("Not implemented");
        }

        @Override
        public Detection detect(ByteBuffer input, Hint hint) {
            throw new UnsupportedOperationException("Not implemented");
        }

        @Override
        public Detection detect(String input, Hint hint) {
            return new Detection(detectionLanguage, UTF_8.name(), false);
        }
    }

    private Execution createExecution(SignificanceSearcher searcher) {
        return new Execution(new Chain<>(searcher), Execution.Context.createContextStub());
    }

    private Execution createExecution(SignificanceSearcher searcher, Language language) {
        var context = Execution.Context.createContextStub();
        context.setLinguistics(new MockLinguistics(language));
        return new Execution(new Chain<>(searcher), context);
    }

    @Test
    void testOnSimpleQuery() {
        Query q = new Query();
        q.getRanking().setProfile("significance-ranking");
        AndItem root = new AndItem();
        WordItem tmp;
        tmp = new WordItem("hello", true);
        root.addItem(tmp);

        q.getModel().getQueryTree().setRoot(root);

        SignificanceModel model = significanceModelRegistry.getModel(Language.ENGLISH).get();
        var helloDocumentFrequency = makeDocumentFrequency(model.documentFrequency("hello"));
        Result r = createExecution(searcher).search(q);

        root = (AndItem) r.getQuery().getModel().getQueryTree().getRoot();
        WordItem w0 = (WordItem) root.getItem(0);
        assertEquals(helloDocumentFrequency, w0.getDocumentFrequency());
    }

    @Test
    void testOnSimpleQueryWithRankingOverride() {
        Query q1 = new Query("?query=hello&ranking.significance.useModel=true");
        q1.getRanking().setProfile("significance-ranking");
        AndItem root = new AndItem();
        WordItem tmp;
        tmp = new WordItem("hello", true);
        root.addItem(tmp);

        q1.getModel().getQueryTree().setRoot(root);

        SignificanceModel model = significanceModelRegistry.getModel(Language.ENGLISH).get();
        var helloDocumentFrequency = makeDocumentFrequency(model.documentFrequency("hello"));
        Result r = createExecution(searcher).search(q1);

        root = (AndItem) r.getQuery().getModel().getQueryTree().getRoot();
        WordItem w0 = (WordItem) root.getItem(0);
        assertEquals(helloDocumentFrequency, w0.getDocumentFrequency());

        Query q2 = new Query("?query=hello&ranking.significance.useModel=false");
        q2.getRanking().setProfile("significance-ranking");
        root = new AndItem();
        tmp = new WordItem("hello", true);
        root.addItem(tmp);

        q2.getModel().getQueryTree().setRoot(root);
        Result r2 = createExecution(searcher).search(q2);
        root = (AndItem) r2.getQuery().getModel().getQueryTree().getRoot();
        WordItem w1 = (WordItem) root.getItem(0);
        assertEquals(Optional.empty(), w1.getDocumentFrequency());
    }

    private static Optional<DocumentFrequency> makeDocumentFrequency(com.yahoo.language.significance.DocumentFrequency source) {
        return Optional.of(new DocumentFrequency(source.frequency(), source.corpusSize()));
    }

    @Test
    void testSignificanceValueOnSimpleANDQuery() {

        Query q = new Query();
        q.getRanking().setProfile("significance-ranking");
        AndItem root = new AndItem();
        WordItem tmp;
        tmp = new WordItem("Hello", true);
        root.addItem(tmp);
        tmp = new WordItem("world", true);
        root.addItem(tmp);

        q.getModel().getQueryTree().setRoot(root);

        SignificanceModel model = significanceModelRegistry.getModel(Language.ENGLISH).get();
        var helloDocumentFrequency = makeDocumentFrequency(model.documentFrequency("hello"));
        var worldDocumentFrequency = makeDocumentFrequency(model.documentFrequency("world"));

        Result r = createExecution(searcher).search(q);

        root = (AndItem) r.getQuery().getModel().getQueryTree().getRoot();
        WordItem w0 = (WordItem) root.getItem(0);
        WordItem w1 = (WordItem) root.getItem(1);

        assertEquals(helloDocumentFrequency, w0.getDocumentFrequency());
        assertEquals(worldDocumentFrequency, w1.getDocumentFrequency());

    }

    @Test
    void testSignificanceValueOnRecursiveQuery() {
        Query q = new Query();
        q.getRanking().setProfile("significance-ranking");
        AndItem root = new AndItem();
        WordItem child1 = new WordItem("hello", true);

        AndItem child2 = new AndItem();
        WordItem child2_1 = new WordItem("test", true);

        AndItem child3 = new AndItem();
        AndItem child3_1 = new AndItem();
        WordItem child3_1_1 = new WordItem("usa", true);

        root.addItem(child1);
        root.addItem(child2);
        root.addItem(child3);

        child2.addItem(child2_1);
        child3.addItem(child3_1);
        child3_1.addItem(child3_1_1);

        q.getModel().getQueryTree().setRoot(root);

        var helloDocumentFrequency = getModelDocumentFrequencyWithEnglish("hello");
        var testDocumentFrequency = getModelDocumentFrequencyWithEnglish("test");

        Result r = createExecution(searcher).search(q);

        root = (AndItem) r.getQuery().getModel().getQueryTree().getRoot();
        WordItem w0 = (WordItem) root.getItem(0);
        WordItem w1 = (WordItem) ((AndItem) root.getItem(1)).getItem(0);
        WordItem w3 = (WordItem) ((AndItem) ((AndItem) root.getItem(2)).getItem(0)).getItem(0);

        assertEquals(helloDocumentFrequency, w0.getDocumentFrequency());
        assertEquals(testDocumentFrequency, w1.getDocumentFrequency());
        assertEquals(Optional.of(new DocumentFrequency(2, 16)), w3.getDocumentFrequency());

    }


    @Test
    public void failsOnConflictingSignificanceConfiguration() {
        var musicSchema = new Schema.Builder("music")
                .add(new DocumentSummary.Builder("default").build())
                .add(new RankProfile.Builder("significance-ranking")
                        .setUseSignificanceModel(true)
                        .build())
                .build();
        var albumSchema = new Schema.Builder("album")
                .add(new DocumentSummary.Builder("default").build())
                .add(new RankProfile.Builder("significance-ranking")
                        .setUseSignificanceModel(false)
                        .build())
                .build();
        var searcher = new SignificanceSearcher(
                significanceModelRegistry, new SchemaInfo(List.of(musicSchema, albumSchema), List.of()));

        var query = new Query();
        query.getRanking().setProfile("significance-ranking");

        var result = createExecution(searcher).search(query);
        assertEquals(1, result.hits().getErrorHit().errors().size());

        var errorMessage = getErrorMessage(result);
        assertEquals(
                "Inconsistent 'significance' configuration for the rank profile 'significance-ranking' in the schemas [music, album]. " +
                        "Use 'restrict' to limit the query to a subset of schemas " +
                        "(https://docs.vespa.ai/en/schemas.html#multiple-schemas). " +
                        "Specify same 'significance' configuration for all selected schemas " +
                        "(https://docs.vespa.ai/en/reference/schema-reference.html#significance).",
                errorMessage
        );
    }

    // Tests that follow verify model resolving logic in different scenarios.
    // Test naming convention is as follows:
    // Explicit language - language set in a query
    // Implicit language - language detected automatically
    // Missing language - language without a model
    // Unknown language - Language.UNKNOWN
    // Missing word - word not in a model for the specified language or fallback models
    // Existing word - word in a model for the specified language or fallback models

    private Result searchWord(
            String word,
            Optional<Language> explicitLanguage,
            Optional<Language> implicitLanguage,
            Optional<DocumentFrequency> documentFrequency,
            Optional<Double> significance
    ) {
        var query = new Query();
        explicitLanguage.ifPresent(query.getModel()::setLanguage);
        query.getRanking().setProfile("significance-ranking");

        var queryRoot = new AndItem();
        var queryWord = new WordItem(word, true);

        documentFrequency.ifPresent(queryWord::setDocumentFrequency);
        significance.ifPresent(queryWord::setSignificance);

        queryRoot.addItem(queryWord);
        query.getModel().getQueryTree().setRoot(queryRoot);

        var context = Execution.Context.createContextStub();
        implicitLanguage.ifPresent(language -> context.setLinguistics(new MockLinguistics(language)));
        var execution = new Execution(new Chain<>(searcher), context);
        return execution.search(query);
    }

    private Optional<DocumentFrequency> getModelDocumentFrequencyWithEnglish(String word) {
        SignificanceModel model = significanceModelRegistry.getModel(Language.ENGLISH).get();
        return makeDocumentFrequency(model.documentFrequency(word));
    }

    private static WordItem getFirstWord(Result result) {
        var resultRoot = (AndItem) result.getQuery().getModel().getQueryTree().getRoot();
        return (WordItem) resultRoot.getItem(0);
    }

    private static String getErrorMessage(Result result) {
        return result.hits().getError().getDetailedMessage();
    }

    @Test
    public void testWithMissingExplicitLanguageOnExistingWord() {
        var existingWord = "hello";
        var explicitLanguage = Language.ITALIAN;
        var result = searchWord(
                existingWord,
                Optional.of(explicitLanguage),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );

        var resultWord = getFirstWord(result);
        assertEquals(Optional.empty(), resultWord.getDocumentFrequency());

        var errorMessage = getErrorMessage(result);
        assertEquals("No significance model available for set language ITALIAN", errorMessage);
    }

    @Test
    public void testWithUnknownExplicitLanguageOnExistingWord() {
        var existingWord = "hello";
        var explicitLanguage = Optional.of(Language.UNKNOWN);

        var result = searchWord(
                existingWord,
                explicitLanguage,
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );

        var resultWord = getFirstWord(result);
        var existingDocumentFrequency = getModelDocumentFrequencyWithEnglish(existingWord);
        assertEquals(existingDocumentFrequency, resultWord.getDocumentFrequency());
    }

    @Test
    public void testWithMissingExplicitLanguageOnMissingWord() {
        var missingWord = "ciao";
        var explicitLanguage = Language.ITALIAN;
        var result = searchWord(
                missingWord,
                Optional.of(explicitLanguage),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
        var resultWord = getFirstWord(result);
        assertEquals(Optional.empty(), resultWord.getDocumentFrequency());

        var errorMessage = getErrorMessage(result);
        assertEquals("No significance model available for set language ITALIAN", errorMessage);
    }

    @Test
    public void testWithMissingImplicitLanguageOnExistingWord() {
        var existingWord = "hello";
        var implicitLanguage = Language.ITALIAN;
        var result = searchWord(
                existingWord,
                Optional.empty(),
                Optional.of(implicitLanguage),
                Optional.empty(),
                Optional.empty()
        );
        var resultWord = getFirstWord(result);
        var existingDocumentFrequency = getModelDocumentFrequencyWithEnglish(existingWord);
        assertEquals(existingDocumentFrequency, resultWord.getDocumentFrequency());
    }

    @Test
    public void testWithMissingImplicitLanguageOnMissingWord() {
        var implicitLanguage = Language.ITALIAN;
        var missingWord = "ciao";
        var result = searchWord(
                missingWord,
                Optional.empty(),
                Optional.of(implicitLanguage),
                Optional.empty(),
                Optional.empty()
        );
        var resultWord = getFirstWord(result);

        var existingWord = "hello";
        var documentFrequency = getModelDocumentFrequencyWithEnglish(existingWord);
        var count = documentFrequency.get().count();
        var defaultDocumentFrequency = Optional.of(new DocumentFrequency(1, count));

        assertEquals(defaultDocumentFrequency, resultWord.getDocumentFrequency());
    }

    // Tests for upper case words in a query
    @ParameterizedTest
    @ValueSource(strings = {"Hello", "HeLlo", "HELLO"})
    public void testWithUpperCaseWord(String wordWithUpperCase) {
        var result = searchWord(
                wordWithUpperCase,
                Optional.of(Language.ENGLISH),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
        var resultWord = getFirstWord(result);

        var lowerCaseWord = "hello";
        var documentFrequency = getModelDocumentFrequencyWithEnglish(lowerCaseWord);

        assertEquals(documentFrequency, resultWord.getDocumentFrequency());
    }

    @Test
    public void testQueryWithDocumentFrequency() {
        var word = "hello";
        var explicitLanguage = Optional.of(Language.ENGLISH);
        var queryDocumentFrequency = Optional.of(new DocumentFrequency(1, 1));

        var result = searchWord(
                word,
                explicitLanguage,
                Optional.empty(),
                queryDocumentFrequency,
                Optional.empty()
        );

        var resultDocumentFrequency = getFirstWord(result).getDocumentFrequency();
        var modelDocumentFrequency = getModelDocumentFrequencyWithEnglish(word);

        assertNotEquals(queryDocumentFrequency, modelDocumentFrequency);
        assertEquals(resultDocumentFrequency, queryDocumentFrequency);
    }

    @Test
    public void testQueryWithSignificance() {
        var word = "hello";
        var explicitLanguage = Optional.of(Language.ENGLISH);
        var querySignificance = Optional.of(0.6);

        var result = searchWord(
                word,
                explicitLanguage,
                Optional.empty(),
                Optional.empty(),
                querySignificance
        );

        var resultSignificance = getFirstWord(result).getSignificance();
        assertEquals(querySignificance.get(), resultSignificance);

        var resultDocumentFrequency = getFirstWord(result).getDocumentFrequency();
        assertEquals(resultDocumentFrequency, Optional.empty());
    }

    @Test
    public void testQueryWithDocumentFrequencyAndSignificance() {
        var word = "hello";
        var explicitLanguage = Optional.of(Language.ENGLISH);
        var queryDocumentFrequency = Optional.of(new DocumentFrequency(1, 1));
        var querySignificance = Optional.of(0.6);

        var result = searchWord(
                word,
                explicitLanguage,
                Optional.empty(),
                queryDocumentFrequency,
                querySignificance
        );
        var firstWord = getFirstWord(result);

        var resultDocumentFrequency = firstWord.getDocumentFrequency();
        var modelDocumentFrequency = getModelDocumentFrequencyWithEnglish(word);

        assertNotEquals(queryDocumentFrequency, modelDocumentFrequency);
        assertEquals(resultDocumentFrequency, queryDocumentFrequency);

        var resultSignificance = firstWord.getSignificance();
        assertEquals(querySignificance.get(), resultSignificance);
    }
}