// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.ml;

import com.google.common.collect.ImmutableList;
import com.yahoo.config.model.ApplicationPackageTester;
import ai.vespa.rankingexpression.importer.configmodelview.MlModelImporter;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.io.IOUtils;
import com.yahoo.path.Path;
import com.yahoo.searchdefinition.RankingConstant;
import ai.vespa.rankingexpression.importer.lightgbm.LightGBMImporter;
import ai.vespa.rankingexpression.importer.onnx.OnnxImporter;
import ai.vespa.rankingexpression.importer.tensorflow.TensorFlowImporter;
import ai.vespa.rankingexpression.importer.vespa.VespaImporter;
import ai.vespa.rankingexpression.importer.xgboost.XGBoostImporter;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.serialization.TypedBinaryFormat;
import com.yahoo.vespa.model.VespaModel;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

/**
 * Helper for testing of imported models.
 * More duplicated functionality across tests on imported models should be moved here
 *
 * @author bratseth
 */
public class ImportedModelTester {

    private final ImmutableList<MlModelImporter> importers = ImmutableList.of(new TensorFlowImporter(),
                                                                              new OnnxImporter(),
                                                                              new LightGBMImporter(),
                                                                              new XGBoostImporter(),
                                                                              new VespaImporter());

    private final String modelName;
    private final Path applicationDir;

    public ImportedModelTester(String modelName, Path applicationDir) {
        this.modelName = modelName;
        this.applicationDir = applicationDir;
    }

    public VespaModel createVespaModel() {
        try {
            DeployState.Builder state = new DeployState.Builder();
            state.applicationPackage(ApplicationPackageTester.create(applicationDir.toString()).app());
            state.modelImporters(importers);
            return new VespaModel(state.build());
        }
        catch (SAXException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Verifies that the constant with the given name exists, and - only if an expected size is given -
     * that the content of the constant is available and has the expected size.
     */
    public void assertLargeConstant(String constantName, VespaModel model, Optional<Long> expectedSize) {
        try {
            Path constantApplicationPackagePath = Path.fromString("models.generated/" + modelName + "/constants").append(constantName + ".tbf");
            RankingConstant rankingConstant = model.rankingConstants().get(constantName);
            assertEquals(constantName, rankingConstant.getName());
            assertTrue(rankingConstant.getFileName().endsWith(constantApplicationPackagePath.toString()));

            if (expectedSize.isPresent()) {
                Path constantPath = applicationDir.append(constantApplicationPackagePath);
                assertTrue("Constant file '" + constantPath + "' has been written",
                           constantPath.toFile().exists());
                Tensor deserializedConstant = TypedBinaryFormat.decode(Optional.empty(),
                                                                       GrowableByteBuffer.wrap(IOUtils.readFileBytes(constantPath.toFile())));
                assertEquals(expectedSize.get().longValue(), deserializedConstant.size());
            }
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
