// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchers;

import com.google.common.util.concurrent.MoreExecutors;

import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.config.subscription.RawSource;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.IndexModel;
import com.yahoo.prelude.SearchDefinition;
import com.yahoo.search.Query;
import com.yahoo.search.query.parser.Parsable;
import com.yahoo.search.query.parser.ParserEnvironment;
import com.yahoo.search.query.QueryTree;
import com.yahoo.search.rendering.RendererRegistry;
import com.yahoo.search.Result;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.yql.YqlParser;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.vespa.config.search.AttributesConfig;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author arnej
 */
public class ValidateNearestNeighborTestCase {

    ValidateNearestNeighborSearcher searcher;

    public ValidateNearestNeighborTestCase() {
        searcher = new ValidateNearestNeighborSearcher(
                ConfigGetter.getConfig(AttributesConfig.class,
                                       "raw:",
                                       new RawSource("attribute[5]\n" +
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
                                                     "attribute[4].tensortype          tensor(x[3],y[1])\n"
        )));
    }

    private static TensorType tt_dense_dvector_3 = TensorType.fromSpec("tensor(x[3])");
    private static TensorType tt_dense_dvector_2 = TensorType.fromSpec("tensor(x[2])");
    private static TensorType tt_dense_fvector_3 = TensorType.fromSpec("tensor<float>(x[3])");
    private static TensorType tt_dense_matrix_xy = TensorType.fromSpec("tensor(x[3],y[1])");
    private static TensorType tt_sparse_vector_x = TensorType.fromSpec("tensor(x{})");

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
        return "select * from sources * where [{\"targetHits\":1}]nearestNeighbor(" + attributeTensor + ", " + queryTensor + ");";
    }

    @Test
    public void testValidQueryDoubleVectors() {
        String q = makeQuery("dvector", "qvector");
        Tensor t = makeTensor(tt_dense_dvector_3);
        Result r = doSearch(searcher, q, t);
        assertNull(r.hits().getError());
    }

    @Test
    public void testValidQueryFloatVectors() {
        String q = makeQuery("fvector", "qvector");
        Tensor t = makeTensor(tt_dense_fvector_3);
        Result r = doSearch(searcher, q, t);
        assertNull(r.hits().getError());
    }

    @Test
    public void testValidQueryDoubleVectorAgainstFloatVector() {
        String q = makeQuery("dvector", "qvector");
        Tensor t = makeTensor(tt_dense_fvector_3);
        Result r = doSearch(searcher, q, t);
        assertNull(r.hits().getError());
    }

    @Test
    public void testValidQueryFloatVectorAgainstDoubleVector() {
        String q = makeQuery("fvector", "qvector");
        Tensor t = makeTensor(tt_dense_dvector_3);
        Result r = doSearch(searcher, q, t);
        assertNull(r.hits().getError());
    }

    private static void assertErrMsg(String message, Result r) {
        assertEquals(ErrorMessage.createIllegalQuery(message), r.hits().getError());
    }

    static String desc(String field, String qt, int th, String errmsg) {
        StringBuilder r = new StringBuilder();
        r.append("NEAREST_NEIGHBOR {");
        r.append("field=").append(field);
        r.append(",queryTensorName=").append(qt);
        r.append(",hnsw.exploreAdditionalHits=0");
        r.append(",distanceThreshold=Infinity");
        r.append(",approximate=true");
        r.append(",targetHits=").append(th);
        r.append("} ").append(errmsg);
        return r.toString();
    }

    @Test
    public void testMissingTargetNumHits() {
        String q = "select * from sources * where nearestNeighbor(dvector,qvector);";
        Tensor t = makeTensor(tt_dense_dvector_3);
        Result r = doSearch(searcher, q, t);
        assertErrMsg(desc("dvector", "qvector", 0, "has invalid targetHits 0: Must be >= 1"), r);
    }

    @Test
    public void testMissingQueryTensor() {
        String q = makeQuery("dvector", "foo");
        Tensor t = makeTensor(tt_dense_dvector_3);
        Result r = doSearch(searcher, q, t);
        assertErrMsg(desc("dvector", "foo", 1, "requires a tensor rank feature query(foo) but this is not present"), r);
    }

    @Test
    public void testWrongTensorType() {
        String q = makeQuery("dvector", "qvector");
        Tensor t = makeTensor(tt_dense_dvector_2, 2);
        Result r = doSearch(searcher, q, t);
        assertErrMsg(desc("dvector", "qvector", 1, "field type tensor(x[3]) does not match query type tensor(x[2])"), r);
    }

    @Test
    public void testNotAttribute() {
        String q = makeQuery("foo", "qvector");
        Tensor t = makeTensor(tt_dense_dvector_3);
        Result r = doSearch(searcher, q, t);
        assertErrMsg(desc("foo", "qvector", 1, "field is not an attribute"), r);
    }

    @Test
    public void testWrongAttributeType() {
        String q = makeQuery("simple", "qvector");
        Tensor t = makeTensor(tt_dense_dvector_3);
        Result r = doSearch(searcher, q, t);
        assertErrMsg(desc("simple", "qvector", 1, "field is not a tensor"), r);
    }

    @Test
    public void testSparseTensor() {
        String q = makeQuery("sparse", "qvector");
        Tensor t = makeTensor(tt_sparse_vector_x);
        Result r = doSearch(searcher, q, t);
        assertErrMsg(desc("sparse", "qvector", 1, "tensor type tensor(x{}) is not a dense vector"), r);
    }

    @Test
    public void testMatrix() {
        String q = makeQuery("matrix", "qvector");
        Tensor t = makeMatrix(tt_dense_matrix_xy);
        Result r = doSearch(searcher, q, t);
        assertErrMsg(desc("matrix", "qvector", 1, "tensor type tensor(x[3],y[1]) is not a dense vector"), r);
    }

    private static Result doSearch(ValidateNearestNeighborSearcher searcher, String yqlQuery, Tensor qTensor) {
        QueryTree queryTree = new YqlParser(new ParserEnvironment()).parse(new Parsable().setQuery(yqlQuery));
        Query query = new Query();
        query.getModel().getQueryTree().setRoot(queryTree.getRoot());
        query.getRanking().getFeatures().put("query(qvector)", qTensor);
        SearchDefinition searchDefinition = new SearchDefinition("document");
        IndexFacts indexFacts = new IndexFacts(new IndexModel(searchDefinition));
        Execution.Context context = new Execution.Context(null, indexFacts, null, new RendererRegistry(MoreExecutors.directExecutor()), new SimpleLinguistics());
        return new Execution(searcher, context).search(query);
    }

}
