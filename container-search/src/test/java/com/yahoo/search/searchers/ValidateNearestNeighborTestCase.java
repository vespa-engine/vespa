// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchers;

import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.language.Language;
import com.yahoo.language.process.Embedder;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.IndexModel;
import com.yahoo.prelude.SearchDefinition;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.query.QueryTree;
import com.yahoo.search.query.parser.Parsable;
import com.yahoo.search.query.parser.ParserEnvironment;
import com.yahoo.search.query.profile.QueryProfile;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.QueryProfileType;
import com.yahoo.search.query.profile.types.QueryProfileTypeRegistry;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.schema.Cluster;
import com.yahoo.search.schema.RankProfile;
import com.yahoo.search.schema.Schema;
import com.yahoo.search.schema.SchemaInfo;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.yql.YqlParser;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.vespa.config.search.AttributesConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author arnej
 */
public class ValidateNearestNeighborTestCase {

    ValidateNearestNeighborSearcher searcher;

    @SuppressWarnings("deprecation")
    public ValidateNearestNeighborTestCase() {
        searcher = new ValidateNearestNeighborSearcher(
                ConfigGetter.getConfig(AttributesConfig.class,
                                       "raw:" +
                                                     "attribute[9]\n" +
                                                     "attribute[0].name                simple\n" +
                                                     "attribute[0].datatype            INT32\n" +
                                                     "attribute[1].name                dvector\n" +
                                                     "attribute[1].datatype            TENSOR\n" +
                                                     "attribute[1].tensortype          tensor(x[3])\n" +
                                                     "attribute[2].name                fvector\n" +
                                                     "attribute[2].datatype            TENSOR\n" +
                                                     "attribute[2].tensortype          tensor<float>(x[3])\n" +
                                                     "attribute[3].name                sparse\n" +
                                                     "attribute[3].datatype            TENSOR\n" +
                                                     "attribute[3].tensortype          tensor(x{})\n" +
                                                     "attribute[4].name                matrix\n" +
                                                     "attribute[4].datatype            TENSOR\n" +
                                                     "attribute[4].tensortype          tensor(x[3],y[1])\n" +
                                                     "attribute[5].name                threetypes\n" +
                                                     "attribute[5].datatype            TENSOR\n" +
                                                     "attribute[5].tensortype          tensor(x[42])\n" +
                                                     "attribute[6].name                threetypes\n" +
                                                     "attribute[6].datatype            TENSOR\n" +
                                                     "attribute[6].tensortype          tensor(x[3])\n" +
                                                     "attribute[7].name                threetypes\n" +
                                                     "attribute[7].datatype            TENSOR\n" +
                                                     "attribute[7].tensortype          tensor(x{})\n" +
                                                     "attribute[8].name                mixeddvector\n" +
                                                     "attribute[8].datatype            TENSOR\n" +
                                                     "attribute[8].tensortype          tensor(a{},x[3])\n"
        ));
    }

    private static SchemaInfo createSchemaInfo() {
        List<Schema> schemas = new ArrayList<>();
        RankProfile.Builder common = new RankProfile.Builder("default")
                                             .addInput("query(qvector)", RankProfile.InputType.fromSpec("tensor<float>(x[3])"));
        schemas.add(new Schema.Builder("document").add(common.build()).build());
        List<Cluster> clusters = new ArrayList<>();
        clusters.add(new Cluster.Builder("test").addSchema("document").build());
        return new SchemaInfo(schemas, clusters);
    }

    private static final TensorType tt_dense_dvector_42 = TensorType.fromSpec("tensor(x[42])");
    private static final TensorType tt_dense_dvector_3 = TensorType.fromSpec("tensor(x[3])");
    private static final TensorType tt_dense_dvector_2 = TensorType.fromSpec("tensor(x[2])");
    private static final TensorType tt_dense_fvector_3 = TensorType.fromSpec("tensor<float>(x[3])");
    private static final TensorType tt_dense_matrix_xy = TensorType.fromSpec("tensor(x[3],y[1])");
    private static final TensorType tt_sparse_vector_x = TensorType.fromSpec("tensor(x{})");

    @Test
    void testValidQueryDoubleVectors() {
        String q = makeQuery("dvector", "qvector");
        Tensor t = makeTensor(tt_dense_dvector_3);
        Result r = doSearch(searcher, q, t);
        assertNull(r.hits().getError());
    }

    @Test
    void testValidQueryFloatVectors() {
        String q = makeQuery("fvector", "qvector");
        Tensor t = makeTensor(tt_dense_fvector_3);
        Result r = doSearch(searcher, q, t);
        assertNull(r.hits().getError());
    }

    @Test
    void testValidQueryDoubleVectorAgainstFloatVector() {
        String q = makeQuery("dvector", "qvector");
        Tensor t = makeTensor(tt_dense_fvector_3);
        Result r = doSearch(searcher, q, t);
        assertNull(r.hits().getError());
    }

    @Test
    void testValidQueryFloatVectorAgainstDoubleVector() {
        String q = makeQuery("fvector", "qvector");
        Tensor t = makeTensor(tt_dense_dvector_3);
        Result r = doSearch(searcher, q, t);
        assertNull(r.hits().getError());
    }

    @Test
    void testvalidQueryMixedFieldTensor() {
        String q = makeQuery("mixeddvector", "qvector");
        Tensor t = makeTensor(tt_dense_dvector_3);
        Result r = doSearch(searcher, q, t);
        assertNull(r.hits().getError());
    }

    private static void assertErrMsg(String message, Result r) {
        assertEquals(ErrorMessage.createIllegalQuery(message), r.hits().getError());
    }

    static String desc(String field, String qt, int th, String errmsg) {
        return "NEAREST_NEIGHBOR {" +
               "field=" + field +
               ",queryTensorName=" + qt +
               ",hnsw.exploreAdditionalHits=0" +
               ",distanceThreshold=Infinity" +
               ",approximate=true" +
               ",targetHits=" + th +
               "} " + errmsg;
    }

    @Test
    void testMissingTargetNumHits() {
        String q = "select * from sources * where nearestNeighbor(dvector,qvector)";
        Tensor t = makeTensor(tt_dense_dvector_3);
        Result r = doSearch(searcher, q, t);
        assertErrMsg(desc("dvector", "qvector", 0, "has invalid targetHits 0: Must be >= 1"), r);
    }

    @Test
    void testMissingQueryTensor() {
        String q = makeQuery("dvector", "foo");
        Tensor t = makeTensor(tt_dense_dvector_3);
        Result r = doSearch(searcher, q, t);
        assertErrMsg(desc("dvector", "foo", 1, "requires a tensor rank feature named 'query(foo)' but this is not present"), r);
    }

    @Test
    void testWrongTensorType() {
        String q = makeQuery("dvector", "qvector");
        Tensor t = makeTensor(tt_dense_dvector_2, 2);
        Result r = doSearch(searcher, q, t);
        assertErrMsg(desc("dvector", "qvector", 1, "field type tensor(x[3]) does not match query type tensor(x[2])"), r);
    }

    @Test
    void testNotAttribute() {
        String q = makeQuery("foo", "qvector");
        Tensor t = makeTensor(tt_dense_dvector_3);
        Result r = doSearch(searcher, q, t);
        assertErrMsg(desc("foo", "qvector", 1, "field is not an attribute"), r);
    }

    @Test
    void testWrongAttributeType() {
        String q = makeQuery("simple", "qvector");
        Tensor t = makeTensor(tt_dense_dvector_3);
        Result r = doSearch(searcher, q, t);
        assertErrMsg(desc("simple", "qvector", 1, "field is not a tensor"), r);
    }

    @Test
    void testSeveralAttributesWithSameName() {
        String q = makeQuery("threetypes", "qvector");
        Tensor t1 = makeTensor(tt_dense_fvector_3);
        Result r1 = doSearch(searcher, q, t1);
        assertNull(r1.hits().getError());
        Tensor t2 = makeTensor(tt_dense_dvector_42, 42);
        Result r2 = doSearch(searcher, q, t2);
        assertNull(r2.hits().getError());
        Tensor t3 = makeTensor(tt_dense_dvector_2, 2);
        Result r3 = doSearch(searcher, q, t3);
        assertErrMsg(desc("threetypes", "qvector", 1, "field type tensor(x[42]) does not match query type tensor(x[2])"), r3);
    }

    @Test
    void testSparseTensor() {
        String q = makeQuery("sparse", "qvector");
        Tensor t = makeTensor(tt_sparse_vector_x);
        Result r = doSearch(searcher, q, t);
        assertErrMsg(desc("sparse", "qvector", 1, "field type tensor(x{}) is not supported by nearest neighbor searcher"), r);
    }

    @Test
    void testMatrix() {
        String q = makeQuery("matrix", "qvector");
        Tensor t = makeMatrix(tt_dense_matrix_xy);
        Result r = doSearch(searcher, q, t);
        assertErrMsg(desc("matrix", "qvector", 1, "field type tensor(x[3],y[1]) is not supported by nearest neighbor searcher"), r);
    }

    @Test
    void testWithQueryProfileArgument() {
        var embedder = new MockEmbedder("test text",
                                        Language.UNKNOWN,
                                        Tensor.from("tensor<float>(x[3]):[1.0, 2.0, 3.0]"));
        var registry = new QueryProfileRegistry();
        var profile = new QueryProfile("test");
        profile.set("ranking.features.query(qvector)", "embed(@foo)", registry);
        registry.register(profile);
        var queryString = makeQuery("fvector", "qvector");
        var query = new Query.Builder()
                            .setSchemaInfo(createSchemaInfo())
                            .setQueryProfile(registry.compile().findQueryProfile("test"))
                            .setEmbedder(embedder)
                            .setRequestMap(Map.of("foo", "test text"))
                            .build();
        setYqlQuery(query, queryString);
        var result = doSearch(searcher, query);
        assertNull(result.hits().getError());
    }

    private Tensor makeTensor(TensorType tensorType) {
        return makeTensor(tensorType, 3);
    }

    private Tensor makeTensor(TensorType tensorType, int numCells) {
        Tensor.Builder tensorBuilder = Tensor.Builder.of(tensorType);
        double dv = 1.0;
        String tensorDimension = "x";
        for (long label = 0; label < numCells; label++) {
            tensorBuilder.cell()
                         .label(tensorDimension, label)
                         .value(dv);
            dv += 1.0;
        }
        return tensorBuilder.build();
    }

    private Tensor makeMatrix(TensorType tensorType) {
        Tensor.Builder tensorBuilder = Tensor.Builder.of(tensorType);
        double dv = 1.0;
        String tensorDimension = "x";
        for (long label = 0; label < 3; label++) {
            tensorBuilder.cell()
                         .label("y", 0L)
                         .label(tensorDimension, label)
                         .value(dv);
            dv += 1.0;
        }
        return tensorBuilder.build();
    }

    private String makeQuery(String attributeTensor, String queryTensor) {
        return "select * from sources * where [{targetHits:1}]nearestNeighbor(" + attributeTensor + ", " + queryTensor + ")";
    }

    private static Result doSearch(ValidateNearestNeighborSearcher searcher, String yqlQuery, Tensor qTensor) {
        return doSearch(searcher, setYqlQuery(new Query(), yqlQuery), qTensor);
    }

    private static Query setYqlQuery(Query query, String yqlQuery) {
        QueryTree queryTree = new YqlParser(new ParserEnvironment()).parse(new Parsable().setQuery(yqlQuery));
        query.getModel().getQueryTree().setRoot(queryTree.getRoot());
        return query;
    }

    private static Result doSearch(ValidateNearestNeighborSearcher searcher, Query query, Tensor qTensor) {
        query.getRanking().getFeatures().put("query(qvector)", qTensor);
        return doSearch(searcher, query);
    }

    private static Result doSearch(ValidateNearestNeighborSearcher searcher, Query query) {
        SearchDefinition searchDefinition = new SearchDefinition("document");
        IndexFacts indexFacts = new IndexFacts(new IndexModel(searchDefinition));
        return new Execution(searcher, Execution.Context.createContextStub(indexFacts)).search(query);
    }

    private static final class MockEmbedder implements Embedder {

        private final String expectedText;
        private final Language expectedLanguage;
        private final Tensor tensorToReturn;

        public MockEmbedder(String expectedText,
                            Language expectedLanguage,
                            Tensor tensorToReturn) {
            this.expectedText = expectedText;
            this.expectedLanguage = expectedLanguage;
            this.tensorToReturn = tensorToReturn;
        }

        @Override
        public List<Integer> embed(String text, Embedder.Context context) {
            fail("Unexpected call");
            return null;
        }

        @Override
        public Tensor embed(String text, Embedder.Context context, TensorType tensorType) {
            assertEquals(expectedText, text);
            assertEquals(expectedLanguage, context.getLanguage());
            assertEquals(tensorToReturn.type(), tensorType);
            return tensorToReturn;
        }

    }

}
