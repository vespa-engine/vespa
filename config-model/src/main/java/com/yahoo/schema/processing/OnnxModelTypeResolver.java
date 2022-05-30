// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.schema.OnnxModel;
import com.yahoo.schema.RankProfile;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
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

    public OnnxModelTypeResolver(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        if (documentsOnly) return;
        for (OnnxModel onnxModel : schema.declaredOnnxModels().values())
            onnxModel.setModelInfo(OnnxModelInfo.load(onnxModel.getFileName(), schema.applicationPackage()));
        for (RankProfile profile : rankProfileRegistry.rankProfilesOf(schema)) {
            for (OnnxModel onnxModel : profile.declaredOnnxModels().values())
                onnxModel.setModelInfo(OnnxModelInfo.load(onnxModel.getFileName(), schema.applicationPackage()));
        }
    }

}
