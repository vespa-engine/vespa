// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.ml;

import ai.vespa.models.evaluation.ModelsEvaluator;
import ai.vespa.rankingexpression.importer.configmodelview.MlModelImporter;
import ai.vespa.rankingexpression.importer.lightgbm.LightGBMImporter;
import ai.vespa.rankingexpression.importer.onnx.OnnxImporter;
import ai.vespa.rankingexpression.importer.tensorflow.TensorFlowImporter;
import ai.vespa.rankingexpression.importer.vespa.VespaImporter;
import ai.vespa.rankingexpression.importer.xgboost.XGBoostImporter;
import com.google.common.collect.ImmutableList;
import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.filedistribution.fileacquirer.FileAcquirer;
import com.yahoo.filedistribution.fileacquirer.MockFileAcquirer;
import com.yahoo.io.IOUtils;
import com.yahoo.schema.derived.RankProfileList;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.vespa.config.search.core.OnnxModelsConfig;
import com.yahoo.vespa.config.search.core.RankingConstantsConfig;
import com.yahoo.vespa.config.search.core.RankingExpressionsConfig;
import com.yahoo.vespa.model.VespaModel;
import net.jpountz.lz4.LZ4FrameOutputStream;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * A ModelsEvaluator object is usually injected automatically in a component if
 * requested. This class is for creating a ModelsEvaluator so that the component
 * can be properly unit tested. Pass a directory containing model files, such
 * as the application's "models" directory, and it will return a ModelsEvaluator
 * for the imported models.
 *
 * For use in testing only.
 *
 * @author lesters
 */
public class ModelsEvaluatorTester {

    private static final ImmutableList<MlModelImporter> importers = ImmutableList.of(new TensorFlowImporter(),
            new OnnxImporter(),
            new LightGBMImporter(),
            new XGBoostImporter(),
            new VespaImporter());

    private static final String modelEvaluationServices = "<services version=\"1.0\">" +
            "  <container version=\"1.0\">" +
            "    <model-evaluation/>" +
            "  </container>" +
            "</services>";

    /**
     * Create a ModelsEvaluator from the models found in the modelsPath. Does
     * not need to be in a application package.
     *
     * @param modelsPath Path to a directory containing models to import
     * @return a ModelsEvaluator containing the imported models
     */
    public static ModelsEvaluator create(String modelsPath) {
        File temporaryApplicationDir = null;
        try {
            temporaryApplicationDir = createTemporaryApplicationDir(modelsPath);
            MockFileRegistry fileRegistry = new MockFileBlobRegistry(temporaryApplicationDir);
            RankProfileList rankProfileList = createRankProfileList(temporaryApplicationDir, fileRegistry);

            RankProfilesConfig rankProfilesConfig = getRankProfilesConfig(rankProfileList);
            RankingConstantsConfig rankingConstantsConfig = getRankingConstantConfig(rankProfileList);
            RankingExpressionsConfig rankingExpressionsConfig = getRankingExpressionsConfig(rankProfileList);
            OnnxModelsConfig onnxModelsConfig = getOnnxModelsConfig(rankProfileList);
            FileAcquirer files = createFileAcquirer(fileRegistry, temporaryApplicationDir);

            return new ModelsEvaluator(rankProfilesConfig, rankingConstantsConfig, rankingExpressionsConfig, onnxModelsConfig, files);

        } catch (IOException | SAXException e) {
            throw new IllegalArgumentException(e);
        } finally {
            if (temporaryApplicationDir != null) {
                IOUtils.recursiveDeleteDir(temporaryApplicationDir);
            }
        }
    }

    private static File createTemporaryApplicationDir(String modelsPath) throws IOException {
        String tmpDir = Files.exists(Path.of("target")) ? "target" : "";
        File temporaryApplicationDir = Files.createTempDirectory(Path.of(tmpDir), "tmp_").toFile();
        File modelsDir = relativePath(temporaryApplicationDir, ApplicationPackage.MODELS_DIR.toString());
        IOUtils.copyDirectory(new File(modelsPath), modelsDir);
        return temporaryApplicationDir;
    }

    private static RankProfileList createRankProfileList(File appDir, FileRegistry registry) throws IOException, SAXException {
        ApplicationPackage app = new MockApplicationPackage.Builder()
                .withEmptyHosts()
                .withServices(modelEvaluationServices)
                .withRoot(appDir).build();
        DeployState deployState = new DeployState.Builder()
                .applicationPackage(app)
                .fileRegistry(registry)
                .modelImporters(importers).build();

        VespaModel vespaModel = new VespaModel(deployState);
        return vespaModel.rankProfileList();
    }

    private static RankProfilesConfig getRankProfilesConfig(RankProfileList rankProfileList) {
        RankProfilesConfig.Builder builder = new RankProfilesConfig.Builder();
        rankProfileList.getConfig(builder);
        return builder.build();
    }

    private static RankingConstantsConfig getRankingConstantConfig(RankProfileList rankProfileList) {
        RankingConstantsConfig.Builder builder = new RankingConstantsConfig.Builder();
        rankProfileList.getConfig(builder);
        return builder.build();
    }

    private static RankingExpressionsConfig getRankingExpressionsConfig(RankProfileList rankProfileList) {
        RankingExpressionsConfig.Builder builder = new RankingExpressionsConfig.Builder();
        rankProfileList.getConfig(builder);
        return builder.build();
    }

    private static OnnxModelsConfig getOnnxModelsConfig(RankProfileList rankProfileList) {
        OnnxModelsConfig.Builder builder = new OnnxModelsConfig.Builder();
        rankProfileList.getConfig(builder);
        return builder.build();
    }

    private static FileAcquirer createFileAcquirer(MockFileRegistry fileRegistry, File appDir) {
        Map<String, File> fileMap = new HashMap<>();
        for (FileRegistry.Entry entry : fileRegistry.export()) {
            fileMap.put(entry.reference.value(), relativePath(appDir, entry.reference.value()));
        }
        return MockFileAcquirer.returnFiles(fileMap);
    }

    private static File relativePath(File root, String subpath) {
        return new File(root.getAbsolutePath() + File.separator + subpath);
    }

    private static class MockFileBlobRegistry extends MockFileRegistry {

        private final File appDir;

        MockFileBlobRegistry(File appdir) {
            this.appDir = appdir;
        }

        @Override
        public FileReference addBlob(String name, ByteBuffer blob) {
            writeBlob(blob, name);
            return addFile(name);
        }

        private void writeBlob(ByteBuffer blob, String relativePath) {
            try (FileOutputStream fos = new FileOutputStream(new File(appDir, relativePath))) {
                if (relativePath.endsWith(".lz4")) {
                    LZ4FrameOutputStream lz4 = new LZ4FrameOutputStream(fos);
                    lz4.write(blob.array(), blob.arrayOffset(), blob.remaining());
                    lz4.close();
                } else {
                    fos.write(blob.array(), blob.arrayOffset(), blob.remaining());
                }
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed writing temp file", e);
            }
        }

    }

}
