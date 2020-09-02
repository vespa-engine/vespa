// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.searchdefinition.expressiontransforms.OnnxModelTransformer;
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
    public void testOnnxModelFeature() throws Exception {
        VespaModel model = new VespaModelCreatorWithFilePkg("src/test/integration/onnx-file").create();
        DocumentDatabase db = ((IndexedSearchCluster)model.getSearchClusters().get(0)).getDocumentDbs().get(0);

        String modelName = OnnxModelTransformer.toModelName("other/mnist_softmax.onnx");

        // Ranking expression should be transformed from
        //     onnxModel("other/mnist_softmax.onnx", "add")
        // to
        //     onnxModel(other_mnist_softmax_onnx).add

        assertTransformedFeature(db, modelName);
        assertGeneratedConfig(db, modelName);
    }

    private void assertGeneratedConfig(DocumentDatabase db, String modelName) {
        OnnxModelsConfig.Builder builder = new OnnxModelsConfig.Builder();
        ((OnnxModelsConfig.Producer) db).getConfig(builder);
        OnnxModelsConfig config = new OnnxModelsConfig(builder);
        assertEquals(1, config.model().size());
        assertEquals(modelName, config.model(0).name());
    }

    private void assertTransformedFeature(DocumentDatabase db, String modelName) {
        RankProfilesConfig.Builder builder = new RankProfilesConfig.Builder();
        ((RankProfilesConfig.Producer) db).getConfig(builder);
        RankProfilesConfig config = new RankProfilesConfig(builder);
        assertEquals(3, config.rankprofile().size());
        assertEquals("my_profile", config.rankprofile(2).name());
        assertEquals("vespa.rank.firstphase", config.rankprofile(2).fef().property(0).name());
        assertEquals("onnxModel(" + modelName + ").add", config.rankprofile(2).fef().property(0).value());
    }

}
