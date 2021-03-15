// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.io.IOUtils;
import com.yahoo.path.Path;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.vespa.config.search.core.OnnxModelsConfig;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.search.DocumentDatabase;
import com.yahoo.vespa.model.search.IndexedSearchCluster;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RankingExpressionWithOnnxModelTestCase {

    private final Path applicationDir = Path.fromString("src/test/integration/onnx-model/");

    @After
    public void removeGeneratedModelFiles() {
        IOUtils.recursiveDeleteDir(applicationDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile());
    }

    @Test
    public void testOnnxModelFeature() throws Exception  {
        VespaModel model = loadModel(applicationDir);
        assertTransformedFeature(model);
        assertGeneratedConfig(model);

        Path storedApplicationDir = applicationDir.append("copy");
        try {
            storedApplicationDir.toFile().mkdirs();
            IOUtils.copy(applicationDir.append("services.xml").toString(), storedApplicationDir.append("services.xml").toString());
            IOUtils.copyDirectory(applicationDir.append("schemas").toFile(), storedApplicationDir.append("schemas").toFile());
            IOUtils.copyDirectory(applicationDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile(),
                    storedApplicationDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile());

            VespaModel storedModel = loadModel(storedApplicationDir);
            assertTransformedFeature(storedModel);
            assertGeneratedConfig(storedModel);
        }
        finally {
            IOUtils.recursiveDeleteDir(storedApplicationDir.toFile());
        }
    }

    private VespaModel loadModel(Path path) throws Exception {
        FilesApplicationPackage applicationPackage = FilesApplicationPackage.fromFile(path.toFile());
        DeployState state = new DeployState.Builder().applicationPackage(applicationPackage).build();
        return new VespaModel(state);
    }

    private void assertGeneratedConfig(VespaModel model) {
        DocumentDatabase db = ((IndexedSearchCluster)model.getSearchClusters().get(0)).getDocumentDbs().get(0);
        OnnxModelsConfig.Builder builder = new OnnxModelsConfig.Builder();
        ((OnnxModelsConfig.Producer) db).getConfig(builder);
        OnnxModelsConfig config = new OnnxModelsConfig(builder);
        assertEquals(6, config.model().size());

        assertEquals("my_model", config.model(0).name());
        assertEquals(3, config.model(0).input().size());
        assertEquals("second/input:0", config.model(0).input(0).name());
        assertEquals("constant(my_constant)", config.model(0).input(0).source());
        assertEquals("first_input", config.model(0).input(1).name());
        assertEquals("attribute(document_field)", config.model(0).input(1).source());
        assertEquals("third_input", config.model(0).input(2).name());
        assertEquals("rankingExpression(my_function)", config.model(0).input(2).source());
        assertEquals(3, config.model(0).output().size());
        assertEquals("path/to/output:0", config.model(0).output(0).name());
        assertEquals("out", config.model(0).output(0).as());
        assertEquals("path/to/output:1", config.model(0).output(1).name());
        assertEquals("path_to_output_1", config.model(0).output(1).as());
        assertEquals("path/to/output:2", config.model(0).output(2).name());
        assertEquals("path_to_output_2", config.model(0).output(2).as());

        assertEquals("files_model_onnx", config.model(1).name());
        assertEquals(3, config.model(1).input().size());
        assertEquals(3, config.model(1).output().size());
        assertEquals("path/to/output:0", config.model(1).output(0).name());
        assertEquals("path_to_output_0", config.model(1).output(0).as());
        assertEquals("path/to/output:1", config.model(1).output(1).name());
        assertEquals("path_to_output_1", config.model(1).output(1).as());
        assertEquals("path/to/output:2", config.model(1).output(2).name());
        assertEquals("path_to_output_2", config.model(1).output(2).as());
        assertEquals("files_model_onnx", config.model(1).name());

        assertEquals("another_model", config.model(2).name());
        assertEquals("third_input", config.model(2).input(2).name());
        assertEquals("rankingExpression(another_function)", config.model(2).input(2).source());

        assertEquals("files_summary_model_onnx", config.model(3).name());
        assertEquals(3, config.model(3).input().size());
        assertEquals(3, config.model(3).output().size());

        assertEquals("dynamic_model", config.model(5).name());
        assertEquals(1, config.model(5).input().size());
        assertEquals(1, config.model(5).output().size());
        assertEquals("rankingExpression(my_function)", config.model(5).input(0).source());

        assertEquals("unbound_model", config.model(4).name());
        assertEquals(1, config.model(4).input().size());
        assertEquals(1, config.model(4).output().size());
        assertEquals("rankingExpression(my_function)", config.model(4).input(0).source());

    }

    private void assertTransformedFeature(VespaModel model) {
        DocumentDatabase db = ((IndexedSearchCluster)model.getSearchClusters().get(0)).getDocumentDbs().get(0);
        RankProfilesConfig.Builder builder = new RankProfilesConfig.Builder();
        ((RankProfilesConfig.Producer) db).getConfig(builder);
        RankProfilesConfig config = new RankProfilesConfig(builder);
        assertEquals(10, config.rankprofile().size());

        assertEquals("test_model_config", config.rankprofile(2).name());
        assertEquals("rankingExpression(my_function).rankingScript", config.rankprofile(2).fef().property(0).name());
        assertEquals("vespa.rank.firstphase", config.rankprofile(2).fef().property(2).name());
        assertEquals("rankingExpression(firstphase)", config.rankprofile(2).fef().property(2).value());
        assertEquals("rankingExpression(firstphase).rankingScript", config.rankprofile(2).fef().property(3).name());
        assertEquals("onnxModel(my_model).out{d0:1}", config.rankprofile(2).fef().property(3).value());

        assertEquals("test_generated_model_config", config.rankprofile(3).name());
        assertEquals("rankingExpression(my_function).rankingScript", config.rankprofile(3).fef().property(0).name());
        assertEquals("rankingExpression(first_input).rankingScript", config.rankprofile(3).fef().property(2).name());
        assertEquals("rankingExpression(second_input).rankingScript", config.rankprofile(3).fef().property(4).name());
        assertEquals("rankingExpression(third_input).rankingScript", config.rankprofile(3).fef().property(6).name());
        assertEquals("vespa.rank.firstphase", config.rankprofile(3).fef().property(8).name());
        assertEquals("rankingExpression(firstphase)", config.rankprofile(3).fef().property(8).value());
        assertEquals("rankingExpression(firstphase).rankingScript", config.rankprofile(3).fef().property(9).name());
        assertEquals("onnxModel(files_model_onnx).path_to_output_1{d0:1}", config.rankprofile(3).fef().property(9).value());

        assertEquals("test_summary_features", config.rankprofile(4).name());
        assertEquals("rankingExpression(another_function).rankingScript", config.rankprofile(4).fef().property(0).name());
        assertEquals("rankingExpression(firstphase).rankingScript", config.rankprofile(4).fef().property(3).name());
        assertEquals("1", config.rankprofile(4).fef().property(3).value());
        assertEquals("vespa.summary.feature", config.rankprofile(4).fef().property(4).name());
        assertEquals("onnxModel(files_summary_model_onnx).path_to_output_2", config.rankprofile(4).fef().property(4).value());
        assertEquals("vespa.summary.feature", config.rankprofile(4).fef().property(5).name());
        assertEquals("onnxModel(another_model).out", config.rankprofile(4).fef().property(5).value());

        assertEquals("test_dynamic_model", config.rankprofile(5).name());
        assertEquals("rankingExpression(my_function).rankingScript", config.rankprofile(5).fef().property(0).name());
        assertEquals("rankingExpression(firstphase).rankingScript", config.rankprofile(5).fef().property(3).name());
        assertEquals("onnxModel(dynamic_model).my_output{d0:0, d1:1}", config.rankprofile(5).fef().property(3).value());

        assertEquals("test_dynamic_model_2", config.rankprofile(6).name());
        assertEquals("rankingExpression(firstphase).rankingScript", config.rankprofile(6).fef().property(5).name());
        assertEquals("onnxModel(dynamic_model).my_output{d0:0, d1:2}", config.rankprofile(6).fef().property(5).value());

        assertEquals("test_dynamic_model_with_transformer_tokens", config.rankprofile(7).name());
        assertEquals("rankingExpression(my_function).rankingScript", config.rankprofile(7).fef().property(1).name());
        assertEquals("tensor<float>(d0[1],d1[10])((if (d1 < 1 + rankingExpression(__token_length@-1993461420) + 1, 0, if (d1 < 1 + rankingExpression(__token_length@-1993461420) + 1 + rankingExpression(__token_length@-1993461420) + 1, 1, 0))))", config.rankprofile(7).fef().property(1).value());

        assertEquals("test_unbound_model", config.rankprofile(8).name());
        assertEquals("rankingExpression(my_function).rankingScript", config.rankprofile(8).fef().property(0).name());
        assertEquals("rankingExpression(firstphase).rankingScript", config.rankprofile(8).fef().property(3).name());
        assertEquals("onnxModel(unbound_model).my_output{d0:0, d1:1}", config.rankprofile(8).fef().property(3).value());


    }

}
