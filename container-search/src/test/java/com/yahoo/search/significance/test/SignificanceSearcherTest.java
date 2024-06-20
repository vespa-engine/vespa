// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.significance.test;

import com.yahoo.component.chain.Chain;
import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.language.Language;
import com.yahoo.language.significance.SignificanceModel;
import com.yahoo.language.significance.SignificanceModelRegistry;
import com.yahoo.language.significance.impl.DefaultSignificanceModelRegistry;
import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.schema.DocumentSummary;
import com.yahoo.search.schema.RankProfile;
import com.yahoo.search.schema.Schema;
import com.yahoo.search.schema.SchemaInfo;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.significance.SignificanceSearcher;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


import static com.yahoo.test.JunitCompat.assertEquals;

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
        searcher = new SignificanceSearcher(significanceModelRegistry, new SchemaInfo(List.of(schema.build()), List.of()));
    }

    private Execution createExecution(SignificanceSearcher searcher) {
        return new Execution(new Chain<>(searcher), Execution.Context.createContextStub());
    }

    private Execution createExecution() {
        return new Execution(new Chain<>(), Execution.Context.createContextStub());
    }

    @Test
    void testSignificanceValueOnSimpleQuery() {
        Query q = new Query();
        q.getRanking().setProfile("significance-ranking");
        AndItem root = new AndItem();
        WordItem tmp;
        tmp = new WordItem("hello", true);
        root.addItem(tmp);

        q.getModel().getQueryTree().setRoot(root);

        SignificanceModel model = significanceModelRegistry.getModel(Language.ENGLISH).get();
        var helloFrequency = model.documentFrequency("hello");
        var helloSignificanceValue = SignificanceSearcher.calculateIDF(helloFrequency.corpusSize(), helloFrequency.frequency());
        Result r = createExecution(searcher).search(q);

        root = (AndItem) r.getQuery().getModel().getQueryTree().getRoot();
        WordItem w0 = (WordItem) root.getItem(0);
        assertEquals(helloSignificanceValue, w0.getSignificance());
    }

    @Test
    void testSignificanceValueOnSimpleQueryWithRankingOverride() {
        Query q1 = new Query("?query=hello&ranking.significance.useModel=true");
        q1.getRanking().setProfile("significance-ranking");
        AndItem root = new AndItem();
        WordItem tmp;
        tmp = new WordItem("hello", true);
        root.addItem(tmp);

        q1.getModel().getQueryTree().setRoot(root);

        SignificanceModel model = significanceModelRegistry.getModel(Language.ENGLISH).get();
        var helloFrequency = model.documentFrequency("hello");
        var helloSignificanceValue = SignificanceSearcher.calculateIDF(helloFrequency.corpusSize(), helloFrequency.frequency());
        Result r = createExecution(searcher).search(q1);

        root = (AndItem) r.getQuery().getModel().getQueryTree().getRoot();
        WordItem w0 = (WordItem) root.getItem(0);
        assertEquals(helloSignificanceValue, w0.getSignificance());

        Query q2 = new Query("?query=hello&ranking.significance.useModel=false");
        q2.getRanking().setProfile("significance-ranking");
        root = new AndItem();
        tmp = new WordItem("hello", true);
        root.addItem(tmp);

        q2.getModel().getQueryTree().setRoot(root);
        Result r2 = createExecution(searcher).search(q2);
        root = (AndItem) r2.getQuery().getModel().getQueryTree().getRoot();
        WordItem w1 = (WordItem) root.getItem(0);
        assertEquals(0.0, w1.getSignificance());
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
        var helloFrequency = model.documentFrequency("Hello");
        var helloSignificanceValue = SignificanceSearcher.calculateIDF(helloFrequency.corpusSize(), helloFrequency.frequency());

        var worldFrequency = model.documentFrequency("world");
        var worldSignificanceValue = SignificanceSearcher.calculateIDF(worldFrequency.corpusSize(), worldFrequency.frequency());

        Result r = createExecution(searcher).search(q);

        root = (AndItem) r.getQuery().getModel().getQueryTree().getRoot();
        WordItem w0 = (WordItem) root.getItem(0);
        WordItem w1 = (WordItem) root.getItem(1);

        assertEquals(helloSignificanceValue, w0.getSignificance());
        assertEquals(worldSignificanceValue, w1.getSignificance());

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

        SignificanceModel model = significanceModelRegistry.getModel(Language.ENGLISH).get();
        var helloFrequency = model.documentFrequency("hello");
        var helloSignificanceValue = SignificanceSearcher.calculateIDF(helloFrequency.corpusSize(), helloFrequency.frequency());

        var testFrequency = model.documentFrequency("test");
        var testSignificanceValue = SignificanceSearcher.calculateIDF(testFrequency.corpusSize(), testFrequency.frequency());



        Result r = createExecution(searcher).search(q);

        root = (AndItem) r.getQuery().getModel().getQueryTree().getRoot();
        WordItem w0 = (WordItem) root.getItem(0);
        WordItem w1 = (WordItem) ((AndItem) root.getItem(1)).getItem(0);
        WordItem w3 = (WordItem) ((AndItem) ((AndItem) root.getItem(2)).getItem(0)).getItem(0);

        assertEquals(helloSignificanceValue, w0.getSignificance());
        assertEquals(testSignificanceValue, w1.getSignificance());
        assertEquals(SignificanceSearcher.calculateIDF(16, 2), w3.getSignificance());

    }

    @Test
    void testSignificanceValueOnEmptyQuery() {
        Query q = new Query();
        q.getModel().setLanguage(Language.NORWEGIAN_BOKMAL);
        AndItem root = new AndItem();
        WordItem tmp;
        tmp = new WordItem("Hei", true);
        root.addItem(tmp);
        tmp = new WordItem("Verden", true);
        root.addItem(tmp);


        q.getModel().getQueryTree().setRoot(root);
        Result r = createExecution(searcher).search(q);
        root = (AndItem) r.getQuery().getModel().getQueryTree().getRoot();

        WordItem w0 = (WordItem) root.getItem(0);
        WordItem w1 = (WordItem) root.getItem(1);

        Result r0 = createExecution().search(q);
        root = (AndItem) r0.getQuery().getModel().getQueryTree().getRoot();

        WordItem w0_0 = (WordItem) root.getItem(0);
        WordItem w0_1 = (WordItem) root.getItem(1);

        assertEquals(w0_0.getSignificance(), w0.getSignificance());
        assertEquals(w0_1.getSignificance(), w1.getSignificance());

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

        var errorMessage = result.hits().getError();
        assertEquals("Inconsistent 'significance' configuration for the rank profile 'significance-ranking' in the schemas [music, album]. " +
                             "Use 'restrict' to limit the query to a subset of schemas " +
                             "(https://docs.vespa.ai/en/schemas.html#multiple-schemas). " +
                             "Specify same 'significance' configuration for all selected schemas " +
                             "(https://docs.vespa.ai/en/reference/schema-reference.html#significance).",
                     errorMessage.getDetailedMessage());
    }
}
