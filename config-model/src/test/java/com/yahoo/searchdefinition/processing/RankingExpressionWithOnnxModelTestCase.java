// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.vespa.config.search.core.OnnxModelsConfig;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.search.DocumentDatabase;
import com.yahoo.vespa.model.search.IndexedSearchCluster;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithFilePkg;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RankingExpressionWithOnnxModelTestCase {

    @Test
    public void testOnnxModelFeature() {
        VespaModel model = new VespaModelCreatorWithFilePkg("src/test/integration/onnx-model").create();
        DocumentDatabase db = ((IndexedSearchCluster)model.getSearchClusters().get(0)).getDocumentDbs().get(0);
        assertTransformedFeature(db);
        assertGeneratedConfig(db);
    }

    private void assertGeneratedConfig(DocumentDatabase db) {
        OnnxModelsConfig.Builder builder = new OnnxModelsConfig.Builder();
        ((OnnxModelsConfig.Producer) db).getConfig(builder);
        OnnxModelsConfig config = new OnnxModelsConfig(builder);
        assertEquals(3, config.model().size());

        assertEquals("my_model", config.model(1).name());
        assertEquals(3, config.model(1).input().size());
        assertEquals("first_input", config.model(1).input(0).name());
        assertEquals("attribute(document_field)", config.model(1).input(0).source());
        assertEquals("second/input:0", config.model(1).input(1).name());
        assertEquals("constant(my_constant)", config.model(1).input(1).source());
        assertEquals("third_input", config.model(1).input(2).name());
        assertEquals("rankingExpression(my_function)", config.model(1).input(2).source());
        assertEquals(1, config.model(1).output().size());
        assertEquals("path/to/output:0", config.model(1).output(0).name());
        assertEquals("out", config.model(1).output(0).as());

        assertEquals("files_ranking_model_onnx", config.model(0).name());
        assertEquals(0, config.model(0).input().size());
        assertEquals(2, config.model(0).output().size());
        assertEquals("path/to/output:1", config.model(0).output(0).name());
        assertEquals("path_to_output_1", config.model(0).output(0).as());
        assertEquals("path/to/output:2", config.model(0).output(1).name());
        assertEquals("path_to_output_2", config.model(0).output(1).as());

        assertEquals("another_model", config.model(2).name());
        assertEquals("third_input", config.model(2).input(2).name());
        assertEquals("rankingExpression(another_function)", config.model(2).input(2).source());
    }

    private void assertTransformedFeature(DocumentDatabase db) {
        RankProfilesConfig.Builder builder = new RankProfilesConfig.Builder();
        ((RankProfilesConfig.Producer) db).getConfig(builder);
        RankProfilesConfig config = new RankProfilesConfig(builder);
        assertEquals(5, config.rankprofile().size());

        assertEquals("test_model_config", config.rankprofile(2).name());
        assertEquals("rankingExpression(my_function).rankingScript", config.rankprofile(2).fef().property(0).name());
        assertEquals("vespa.rank.firstphase", config.rankprofile(2).fef().property(2).name());
        assertEquals("onnxModel(my_model).out", config.rankprofile(2).fef().property(2).value());

        assertEquals("test_generated_model_config", config.rankprofile(3).name());
        assertEquals("rankingExpression(my_function).rankingScript", config.rankprofile(3).fef().property(0).name());
        assertEquals("rankingExpression(first_input).rankingScript", config.rankprofile(3).fef().property(2).name());
        assertEquals("rankingExpression(second_input).rankingScript", config.rankprofile(3).fef().property(4).name());
        assertEquals("rankingExpression(third_input).rankingScript", config.rankprofile(3).fef().property(6).name());
        assertEquals("vespa.rank.firstphase", config.rankprofile(3).fef().property(8).name());
        assertEquals("onnxModel(files_ranking_model_onnx).path_to_output_1", config.rankprofile(3).fef().property(8).value());

        assertEquals("test_summary_features", config.rankprofile(4).name());
        assertEquals("rankingExpression(another_function).rankingScript", config.rankprofile(4).fef().property(0).name());
        assertEquals("rankingExpression(firstphase).rankingScript", config.rankprofile(4).fef().property(3).name());
        assertEquals("1", config.rankprofile(4).fef().property(3).value());
        assertEquals("vespa.summary.feature", config.rankprofile(4).fef().property(4).name());
        assertEquals("onnxModel(another_model).out", config.rankprofile(4).fef().property(4).value());
        assertEquals("vespa.summary.feature", config.rankprofile(4).fef().property(5).name());
        assertEquals("onnxModel(files_ranking_model_onnx).path_to_output_2", config.rankprofile(4).fef().property(5).value());
    }

}
