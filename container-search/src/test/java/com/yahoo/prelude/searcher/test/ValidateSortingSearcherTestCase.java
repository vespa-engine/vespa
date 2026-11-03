// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.searcher.test;

import com.yahoo.component.chain.Chain;
import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.prelude.fastsearch.DocumentdbInfoConfig;
import com.yahoo.prelude.searcher.ValidateSortingSearcher;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.config.ClusterConfig;
import com.yahoo.search.query.Sorting;
import com.yahoo.search.query.parser.Parsable;
import com.yahoo.search.query.parser.ParserEnvironment;
import com.yahoo.search.schema.Cluster;
import com.yahoo.search.schema.RankProfile;
import com.yahoo.search.schema.Schema;
import com.yahoo.search.schema.SchemaInfo;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.test.QueryTestCase;
import com.yahoo.search.yql.YqlParser;
import com.yahoo.vespa.config.search.AttributesConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Check sorting validation behaves OK.
 *
 * @author Steinar Knutsen
 */
public class ValidateSortingSearcherTestCase {

    private final ValidateSortingSearcher searcher;

    @SuppressWarnings("deprecation")
    public ValidateSortingSearcherTestCase() {
        ClusterConfig.Builder clusterCfg = new ClusterConfig.Builder()
                .clusterName("giraffes");
        String attributesCfg = "file:src/test/java/com/yahoo/prelude/searcher/test/validate_sorting.cfg";
        searcher = new ValidateSortingSearcher(new ClusterConfig(clusterCfg),
                                               ConfigGetter.getConfig(AttributesConfig.class, attributesCfg),
                                               new DocumentdbInfoConfig.Builder().build());
    }

    @Test
    void testBasicValidation() {
        assertNotNull(quoteAndTransform("+a -b +c"));
        assertNotNull(quoteAndTransform("+a"));
        assertNotNull(quoteAndTransform(null));
        assertEquals("[ASCENDING:[rank]]", quoteAndTransform("+[rank]"));
        assertEquals("[ASCENDING:[docid]]", quoteAndTransform("+[docid]"));
        assertEquals("[ASCENDING:[rank]]", quoteAndTransform("+[relevancy]"));
        assertEquals("[ASCENDING:[rank]]", quoteAndTransform("+[relevance]"));
    }

    @Test
    void testDisallowSortingOnTensors() {
        try {
            quoteAndTransform("aTensor");
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("Cannot sort on field 'aTensor' because it is a tensor", e.getMessage());
        }
    }

    @Test
    void testInvalidSpec() {
        assertNull(quoteAndTransform("+a -e +c"));
    }

    @Test
    void featureSortRequiresAllowlistOnIndexedSchema() {
        var searcher = featureSearcher(List.of(indexed("products")), false);
        Result ok = featureSearch(searcher, schemaInfo(indexed("products")),
                                  "?query=a&ranking=products&sorting=" + QueryTestCase.httpEncode("-feature(title_bm25)"));
        assertNull(ok.hits().getError());
        assertEquals("[DESCENDING:feature(title_bm25)]",
                     ok.getQuery().getRanking().getSorting().fieldOrders().toString());

        Result undeclared = featureSearch(searcher, schemaInfo(indexed("products")),
                                          "?query=a&ranking=products&sorting=" + QueryTestCase.httpEncode("-feature(unknown)"));
        assertNotNull(undeclared.hits().getError());
        assertTrue(undeclared.hits().getError().getDetailedMessage().contains("feature(unknown)"));
    }

    @Test
    void featureSortDoesNotInheritAttributeDirectionOrSorter() {
        var searcher = featureSearcher(List.of(indexed("products")), false);
        Result r = featureSearch(searcher, schemaInfo(indexed("products")),
                                 "?query=a&ranking=products&sorting=" + QueryTestCase.httpEncode("feature(foo)"));
        assertNull(r.hits().getError());
        assertEquals("[ASCENDING:feature(foo)]",
                     r.getQuery().getRanking().getSorting().fieldOrders().toString());
    }

    @Test
    void featureSortFailsForStreamingSchema() {
        var searcher = featureSearcher(List.of(streaming("streaming")), true);
        Result r = featureSearch(searcher, schemaInfo(streaming("streaming")),
                                 "?query=a&ranking=products&sorting=" + QueryTestCase.httpEncode("-feature(title_bm25)"));
        assertNotNull(r.hits().getError());
        assertTrue(r.hits().getError().getDetailedMessage().contains("not indexed"));
    }

    @Test
    void featureSortMixedProviderHonorsRestrict() {
        var schemas = List.of(indexed("indexed"), streaming("streaming"));
        var searcher = featureSearcher(schemas, false);
        SchemaInfo schemaInfo = schemaInfo(schemas.toArray(Schema[]::new));

        Result indexedOnly = featureSearch(searcher, schemaInfo,
                "?query=a&ranking=products&restrict=indexed&sorting=" + QueryTestCase.httpEncode("-feature(title_bm25)"));
        assertNull(indexedOnly.hits().getError());

        Result streamingOnly = featureSearch(searcher, schemaInfo,
                "?query=a&ranking=products&restrict=streaming&sorting=" + QueryTestCase.httpEncode("-feature(title_bm25)"));
        assertNotNull(streamingOnly.hits().getError());

        Result both = featureSearch(searcher, schemaInfo,
                "?query=a&ranking=products&sorting=" + QueryTestCase.httpEncode("-feature(title_bm25)"));
        assertNotNull(both.hits().getError());
    }

    @Test
    void featureSortIgnoresStoreOnlyAndForeignSchemas() {
        Schema products = indexed("products");
        Schema archived = new Schema.Builder("archived")
                .add(new RankProfile.Builder("products").build())
                .build();
        Schema other = new Schema.Builder("other")
                .add(new RankProfile.Builder("products").addSortFeature("title_bm25").build())
                .build();
        var searcher = featureSearcher(List.of(products, archived), false,
                List.of(DocumentdbInfoConfig.Documentdb.Mode.Enum.INDEX,
                        DocumentdbInfoConfig.Documentdb.Mode.Enum.STORE_ONLY));
        SchemaInfo schemaInfo = new SchemaInfo(
                List.of(products, archived, other),
                List.of(new Cluster.Builder("giraffes").addSchema("products").addSchema("archived").build(),
                        new Cluster.Builder("elsewhere").addSchema("other").build()));
        Result r = featureSearch(searcher, schemaInfo,
                                 "?query=a&ranking=products&sources=giraffes&sorting=" + QueryTestCase.httpEncode("-feature(title_bm25)"));
        assertNull(r.hits().getError());
    }

    @Test
    void featureSortAllowlistIsIntersectedAcrossIndexedSchemas() {
        Schema allowed = indexed("allowed");
        Schema denied = new Schema.Builder("denied")
                .add(new RankProfile.Builder("products").build())
                .add(new RankProfile.Builder("default").build())
                .build();
        var searcher = featureSearcher(List.of(allowed, denied), false);
        SchemaInfo schemaInfo = schemaInfo(allowed, denied);

        Result unrestricted = featureSearch(searcher, schemaInfo,
                "?query=a&ranking=products&sorting=" + QueryTestCase.httpEncode("-feature(title_bm25)"));
        assertNotNull(unrestricted.hits().getError());
        assertTrue(unrestricted.hits().getError().getDetailedMessage().contains("denied"));

        Result restricted = featureSearch(searcher, schemaInfo,
                "?query=a&ranking=products&restrict=allowed&sorting=" + QueryTestCase.httpEncode("-feature(title_bm25)"));
        assertNull(restricted.hits().getError());
    }

    @Test
    void featureSortFromYqlAnnotationIsValidated() {
        var parser = new YqlParser(new ParserEnvironment());
        parser.parse(new Parsable().setQuery(
                "select * from sources * where true order by {\"function\": \"feature\"}title_bm25 desc"));
        var searcher = featureSearcher(List.of(indexed("products")), false);
        Query q = new Query("?query=a&ranking=products");
        q.getRanking().setSorting(parser.getSorting());
        Result r = new Execution(chainedAsSearchChainStatic(searcher),
                                 Execution.Context.createContextStub(schemaInfo(indexed("products")))).search(q);
        assertNull(r.hits().getError());
        assertInstanceOf(Sorting.FeatureSorter.class, r.getQuery().getRanking().getSorting().fieldOrders().get(0).getSorter());
    }

    private static Schema indexed(String name) {
        return new Schema.Builder(name)
                .add(new RankProfile.Builder("products").addSortFeature("title_bm25").addSortFeature("foo").build())
                .add(new RankProfile.Builder("default").addSortFeature("title_bm25").addSortFeature("foo").build())
                .build();
    }

    private static Schema streaming(String name) {
        return indexed(name);
    }

    private static SchemaInfo schemaInfo(Schema... schemas) {
        Cluster.Builder cluster = new Cluster.Builder("giraffes");
        for (Schema schema : schemas)
            cluster.addSchema(schema.name());
        return new SchemaInfo(List.of(schemas), List.of(cluster.build()));
    }

    @SuppressWarnings("deprecation")
    private static ValidateSortingSearcher featureSearcher(List<Schema> schemas, boolean streaming) {
        List<DocumentdbInfoConfig.Documentdb.Mode.Enum> modes = new ArrayList<>();
        for (Schema schema : schemas) {
            modes.add("streaming".equals(schema.name())
                    ? DocumentdbInfoConfig.Documentdb.Mode.Enum.STREAMING
                    : DocumentdbInfoConfig.Documentdb.Mode.Enum.INDEX);
        }
        return featureSearcher(schemas, streaming, modes);
    }

    @SuppressWarnings("deprecation")
    private static ValidateSortingSearcher featureSearcher(List<Schema> schemas, boolean streaming,
                                                           List<DocumentdbInfoConfig.Documentdb.Mode.Enum> modes) {
        ClusterConfig.Builder clusterCfg = new ClusterConfig.Builder().clusterName("giraffes");
        if (streaming)
            clusterCfg.indexMode(ClusterConfig.IndexMode.Enum.STREAMING);
        DocumentdbInfoConfig.Builder dbs = new DocumentdbInfoConfig.Builder();
        for (int i = 0; i < schemas.size(); i++) {
            var db = new DocumentdbInfoConfig.Documentdb.Builder().name(schemas.get(i).name());
            db.mode(modes.get(i));
            dbs.documentdb(db);
        }
        String attributesCfg = "file:src/test/java/com/yahoo/prelude/searcher/test/validate_sorting.cfg";
        return new ValidateSortingSearcher(new ClusterConfig(clusterCfg),
                                           ConfigGetter.getConfig(AttributesConfig.class, attributesCfg),
                                           dbs.build());
    }

    private static Result featureSearch(Searcher searcher, SchemaInfo schemaInfo, String query) {
        Query q = new Query(query);
        q.setHits(10);
        return new Execution(chainedAsSearchChainStatic(searcher), Execution.Context.createContextStub(schemaInfo)).search(q);
    }

    private static Chain<Searcher> chainedAsSearchChainStatic(Searcher topOfChain) {
        List<Searcher> searchers = new ArrayList<>();
        searchers.add(topOfChain);
        return new Chain<>(searchers);
    }

    @Test
    void testConfigOverride() {
        assertEquals("[ASCENDING:uca(title,en_US,TERTIARY)]", quoteAndTransform("title"));
        assertEquals("[ASCENDING:uca(title,en_US,TERTIARY)]", quoteAndTransform("uca(title)"));
        assertEquals("[ASCENDING:uca(title,en_US,TERTIARY)]", quoteAndTransform("+uca(title)"));
        assertEquals("[ASCENDING:uca(title,en_US,TERTIARY)]", quoteAndTransform("uca(title,en_US)"));
    }

    @Test
    void requireThatQueryLocaleIsDefault() {
        assertEquals("[ASCENDING:lowercase(a)]", quoteAndTransform("a"));
        assertEquals("[ASCENDING:uca(a,en_US,PRIMARY)]", transform("a", "en-US"));
        assertEquals("[ASCENDING:uca(a,en_NO,PRIMARY)]", transform("a", "en-NO"));
        assertEquals("[ASCENDING:uca(a,no_NO,PRIMARY)]", transform("a", "no-NO"));

        assertEquals("[ASCENDING:uca(a,en_US,PRIMARY)]", quoteAndTransform("uca(a)"));
        assertEquals("[ASCENDING:uca(a,en_US,PRIMARY)]", transform("uca(a)", "en-US"));
        assertEquals("[ASCENDING:uca(a,en_NO,PRIMARY)]", transform("uca(a)", "en-NO"));
        assertEquals("[ASCENDING:uca(a,no_NO,PRIMARY)]", transform("uca(a)", "no-NO"));
    }

    private String quoteAndTransform(String sorting) {
        return transform(QueryTestCase.httpEncode(sorting), null);
    }

    private String transform(String sorting, String language) {
        String q = "/?query=a";
        if (sorting != null) {
            q += "&sorting=" + sorting;
        }
        if (language != null) {
            q += "&language=" + language;
        }
        new Query(q);
        Result r = doSearch(searcher, new Query(q), 0, 10);
        if (r.hits().getError() != null) {
            return null;
        }
        if (r.getQuery().getRanking().getSorting() == null) {
            return "";
        }
        return r.getQuery().getRanking().getSorting().fieldOrders().toString();
    }

    private Result doSearch(Searcher searcher, Query query, int offset, int hits) {
        query.setOffset(offset);
        query.setHits(hits);
        return createExecution(searcher).search(query);
    }

    private Execution createExecution(Searcher searcher) {
        return new Execution(chainedAsSearchChain(searcher), Execution.Context.createContextStub());
    }

    private Chain<Searcher> chainedAsSearchChain(Searcher topOfChain) {
        List<Searcher> searchers = new ArrayList<>();
        searchers.add(topOfChain);
        return new Chain<>(searchers);
    }

}
