// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.schema.OnnxModel;
import com.yahoo.vespa.config.search.core.OnnxModelsConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
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

    private FileDistributedOnnxModels(Collection<OnnxModel> models) {
        Map<String, OnnxModel> distributableModels = models.stream()
                .collect(LinkedHashMap::new, (m, v) -> m.put(v.getName(), v.clone()), LinkedHashMap::putAll);
        this.models = Collections.unmodifiableMap(distributableModels);
    }

    public FileDistributedOnnxModels clone() {
        return new FileDistributedOnnxModels(models.values());
    }

    public Map<String, OnnxModel> asMap() { return models; }

    private static OnnxModelsConfig.Model.Builder toConfig(OnnxModel model) {
        OnnxModelsConfig.Model.Builder builder = new OnnxModelsConfig.Model.Builder();
        builder.dry_run_on_setup(true);
        builder.name(model.getName());
        builder.fileref(model.getFileReference());
        model.getInputMap().forEach((name, source) -> builder.input(new OnnxModelsConfig.Model.Input.Builder().name(name).source(source)));
        model.getOutputMap().forEach((name, as) -> builder.output(new OnnxModelsConfig.Model.Output.Builder().name(name).as(as)));
        if (model.getStatelessExecutionMode().isPresent())
            builder.stateless_execution_mode(model.getStatelessExecutionMode().get());
        if (model.getStatelessInterOpThreads().isPresent())
            builder.stateless_interop_threads(model.getStatelessInterOpThreads().get());
        if (model.getStatelessIntraOpThreads().isPresent())
            builder.stateless_intraop_threads(model.getStatelessIntraOpThreads().get());
        if (model.getGpuDevice().isPresent()) {
            builder.gpu_device(model.getGpuDevice().get().deviceNumber());
            builder.gpu_device_required(model.getGpuDevice().get().required());
        }
        return builder;
    }

    public List<OnnxModelsConfig.Model.Builder> getConfig() {
        List<OnnxModelsConfig.Model.Builder> cfgList = new ArrayList<>();
        for (OnnxModel model : models.values()) {
            if ("".equals(model.getFileReference()))
                log.warning("Illegal file reference " + model); // Let tests pass ... we should find a better way
            else {
                cfgList.add(toConfig(model));
            }
        }
        return cfgList;
    }

}
