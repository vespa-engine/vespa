// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.OnnxModel;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.Search;
import com.yahoo.vespa.model.container.search.QueryProfiles;
import com.yahoo.vespa.model.ml.OnnxModelInfo;

/**
 * Processes every "onnx-model" element in the schema. Associates model type
 * information by retrieving from either the ONNX model file directly or from
 * preprocessed information in ZK. Adds missing input and output mappings
 * (assigning default names).
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

        for (OnnxModel onnxModel : search.onnxModels().asMap().values()) {
            OnnxModelInfo onnxModelInfo = OnnxModelInfo.load(onnxModel.getFileName(), search.applicationPackage());

            // Temporary, to disregard type information when model info is not available
            if (onnxModelInfo == null) {
                continue;
            }

            // Add any missing input and output fields that were not specified in the onnx-model configuration
            for (String onnxName : onnxModelInfo.getInputs()) {
                onnxModel.addInputNameMapping(onnxName, OnnxModelInfo.asValidIdentifier(onnxName), false);
            }
            for (String onnxName : onnxModelInfo.getOutputs()) {
                onnxModel.addOutputNameMapping(onnxName, OnnxModelInfo.asValidIdentifier(onnxName), false);
            }

            onnxModel.setModelInfo(onnxModelInfo);
        }
    }

}
