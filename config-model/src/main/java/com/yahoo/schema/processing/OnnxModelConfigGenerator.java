// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.schema.processing;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.schema.OnnxModel;
import com.yahoo.schema.RankProfile;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.expressiontransforms.OnnxModelTransformer;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ConstantNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.vespa.model.container.search.QueryProfiles;
import com.yahoo.vespa.model.ml.OnnxModelInfo;

import java.util.Map;

/**
 * Processes ONNX ranking features of the form:
 *
 *     onnx("files/model.onnx", "path/to/output:1")
 *
 * And generates an "onnx-model" configuration as if it was defined in the profile:
 *
 *   onnx-model files_model_onnx {
 *       file:  "files/model.onnx"
 *   }
 *
 * Inputs and outputs are resolved in OnnxModelTypeResolver, which must be
 * processed after this.
 *
 * @author lesters
 */
public class OnnxModelConfigGenerator extends Processor {

    public OnnxModelConfigGenerator(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        if (documentsOnly) return;
        for (RankProfile profile : rankProfileRegistry.rankProfilesOf(schema)) {
            if (profile.getFirstPhaseRanking() != null) {
                process(profile.getFirstPhaseRanking().getRoot(),  profile);
            }
            if (profile.getSecondPhaseRanking() != null) {
                process(profile.getSecondPhaseRanking().getRoot(), profile);
            }
            for (Map.Entry<String, RankProfile.RankingExpressionFunction> function : profile.getFunctions().entrySet()) {
                process(function.getValue().function().getBody().getRoot(), profile);
            }
            for (ReferenceNode feature : profile.getSummaryFeatures()) {
                process(feature, profile);
            }
        }
    }

    private void process(ExpressionNode node, RankProfile profile) {
        if (node instanceof ReferenceNode) {
            process((ReferenceNode)node, profile);
        } else if (node instanceof CompositeNode) {
            for (ExpressionNode child : ((CompositeNode) node).children()) {
                process(child, profile);
            }
        }
    }

    private void process(ReferenceNode feature, RankProfile profile) {
        if (feature.getName().equals("onnxModel") || feature.getName().equals("onnx")) {
            if (feature.getArguments().size() > 0) {
                if (feature.getArguments().expressions().get(0) instanceof ConstantNode) {
                    ConstantNode node = (ConstantNode) feature.getArguments().expressions().get(0);
                    String path = OnnxModelTransformer.stripQuotes(node.toString());
                    String modelConfigName = OnnxModelTransformer.asValidIdentifier(path);

                    // Only add the configuration if the model can actually be found.
                    if ( ! OnnxModelInfo.modelExists(path, schema.applicationPackage())) {
                        path = ApplicationPackage.MODELS_DIR.append(path).toString();
                        if ( ! OnnxModelInfo.modelExists(path, schema.applicationPackage())) {
                            return;
                        }
                    }

                    OnnxModel onnxModel = profile.onnxModels().get(modelConfigName);
                    if (onnxModel == null)
                        profile.add(new OnnxModel(modelConfigName, path));
                }
            }
        }
    }

}
