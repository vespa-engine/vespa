// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.searchdefinition.processing;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.Version;
import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.path.Path;
import com.yahoo.searchdefinition.OnnxModel;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.expressiontransforms.OnnxModelTransformer;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.model.container.search.QueryProfiles;
import onnx.Onnx;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

/**
 * Processes every "onnx-model" element in the schema. Parses the model file,
 * adds missing input and output mappings (assigning default names), and
 * adds tensor types to all model inputs and outputs.
 *
 * Must be processed before RankingExpressingTypeResolver.
 *
 * @author lesters
 */
public class OnnxModelTypeResolver extends Processor {

    public OnnxModelTypeResolver(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        if (documentsOnly) return;

        for (Map.Entry<String, OnnxModel> entry : search.onnxModels().asMap().entrySet())  {
            OnnxModel modelConfig = entry.getValue();
            try (InputStream inputStream = openModelFile(modelConfig.getFilePath())) {
                Onnx.ModelProto model = Onnx.ModelProto.parseFrom(inputStream);

                // Model inputs - if not defined, assumes a function is provided with a valid name
                for (Onnx.ValueInfoProto valueInfo : model.getGraph().getInputList()) {
                    String onnxInputName = valueInfo.getName();
                    String vespaInputName = OnnxModelTransformer.asValidIdentifier(onnxInputName);
                    modelConfig.addInputNameMapping(onnxInputName, vespaInputName, false);
                    modelConfig.addInputType(onnxInputName, valueInfo.getType());
                }

                // Model outputs
                for (Onnx.ValueInfoProto valueInfo : model.getGraph().getOutputList()) {
                    String onnxOutputName = valueInfo.getName();
                    String vespaOutputName = OnnxModelTransformer.asValidIdentifier(onnxOutputName);
                    modelConfig.addOutputNameMapping(onnxOutputName, vespaOutputName, false);
                    modelConfig.addOutputType(onnxOutputName, valueInfo.getType());
                }

                // Set the first output as default
                if ( ! model.getGraph().getOutputList().isEmpty()) {
                    modelConfig.setDefaultOutput(model.getGraph().getOutput(0).getName());
                }

            } catch (IOException e) {
                throw new IllegalArgumentException("Unable to parse ONNX model", e);
            }
        }
    }

    static boolean modelFileExists(String path, ApplicationPackage app) {
        Path pathInApplicationPackage = Path.fromString(path);
        if (getFile(pathInApplicationPackage, app).exists()) {
            return true;
        }
        if (getFileReference(pathInApplicationPackage, app).isPresent()) {
            return true;
        }
        return false;
    }

    private InputStream openModelFile(Path path) throws FileNotFoundException {
        ApplicationFile file;
        Optional<FileReference> reference;
        Path modelsPath = ApplicationPackage.MODELS_DIR.append(path);

        if ((file = getFile(path)).exists()) {
            return file.createInputStream();
        }
        if ((file = getFile(modelsPath)).exists()) {
            return file.createInputStream();
        }
        if ((reference = getFileReference(path)).isPresent()) {
            return openFromFileRepository(path, reference.get());
        }
        if ((reference = getFileReference(modelsPath)).isPresent()) {
            return openFromFileRepository(modelsPath, reference.get());
        }

        throw new IllegalArgumentException("Unable to find ONNX model file \"" + path + "\" " +
            "in application package or file repository.");
    }

    private ApplicationFile getFile(Path path) {
        return getFile(path, search.applicationPackage());
    }

    private static ApplicationFile getFile(Path path, ApplicationPackage app) {
        return app.getFile(path);
    }

    private static InputStream openFromFileRepository(Path path, FileReference reference) throws FileNotFoundException {
        return new FileInputStream(new File(getFileRepositoryPath(path, reference.value())));
    }

    public static String getFileRepositoryPath(Path path, String fileReference) {
        ConfigserverConfig cfg = new ConfigserverConfig(new ConfigserverConfig.Builder());  // assume defaults
        String fileRefDir = Defaults.getDefaults().underVespaHome(cfg.fileReferencesDir());
        return Paths.get(fileRefDir, fileReference, path.getName()).toString();
    }

    private Optional<FileReference> getFileReference(Path path) {
        return getFileReference(path, search.applicationPackage());
    }

    private static Optional<FileReference> getFileReference(Path path, ApplicationPackage app) {
        Optional<FileRegistry> fileRegistry = getLatestFileRegistry(app);
        if (fileRegistry.isPresent()) {
            for (FileRegistry.Entry file : fileRegistry.get().export()) {
                if (file.relativePath.equals(path.toString())) {
                    return Optional.of(file.reference);
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<FileRegistry> getLatestFileRegistry(ApplicationPackage app) {
        if (app == null) return Optional.empty();
        Optional<Version> latest = app.getFileRegistries().keySet().stream().max(Version::compareTo);
        return latest.isEmpty() ? Optional.empty() : Optional.of(app.getFileRegistries().get(latest.get()));
    }

}
