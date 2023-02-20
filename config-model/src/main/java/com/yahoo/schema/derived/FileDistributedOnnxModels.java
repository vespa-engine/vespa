// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.schema.OnnxModel;
import com.yahoo.vespa.config.search.core.OnnxModelsConfig;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * ONNX models distributed as files.
 *
 * @author bratseth
 */
public class FileDistributedOnnxModels {

    private static final Logger log = Logger.getLogger(FileDistributedOnnxModels.class.getName());

    private final Map<String, OnnxModel> models;

    public FileDistributedOnnxModels(FileRegistry fileRegistry, Collection<OnnxModel> models) {
        Map<String, OnnxModel> distributableModels = new LinkedHashMap<>();
        for (var model : models) {
            model.validate();
            model.register(fileRegistry);
            distributableModels.put(model.getName(), model);
        }
        this.models = Collections.unmodifiableMap(distributableModels);
    }

    public Map<String, OnnxModel> asMap() { return models; }

    public void getConfig(OnnxModelsConfig.Builder builder) {
        for (OnnxModel model : models.values()) {
            if ("".equals(model.getFileReference()))
                log.warning("Illegal file reference " + model); // Let tests pass ... we should find a better way
            else {
                OnnxModelsConfig.Model.Builder modelBuilder = new OnnxModelsConfig.Model.Builder();
                modelBuilder.dry_run_on_setup(true);
                modelBuilder.name(model.getName());
                modelBuilder.fileref(model.getFileReference());
                model.getInputMap().forEach((name, source) -> modelBuilder.input(new OnnxModelsConfig.Model.Input.Builder().name(name).source(source)));
                model.getOutputMap().forEach((name, as) -> modelBuilder.output(new OnnxModelsConfig.Model.Output.Builder().name(name).as(as)));
                if (model.getStatelessExecutionMode().isPresent())
                    modelBuilder.stateless_execution_mode(model.getStatelessExecutionMode().get());
                if (model.getStatelessInterOpThreads().isPresent())
                    modelBuilder.stateless_interop_threads(model.getStatelessInterOpThreads().get());
                if (model.getStatelessIntraOpThreads().isPresent())
                    modelBuilder.stateless_intraop_threads(model.getStatelessIntraOpThreads().get());
                if (model.getGpuDevice().isPresent()) {
                    modelBuilder.gpu_device(model.getGpuDevice().get().deviceNumber());
                    modelBuilder.gpu_device_required(model.getGpuDevice().get().required());
                }
                builder.model(modelBuilder);
            }
        }
    }

}
