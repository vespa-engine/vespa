// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.OnnxModel;
import com.yahoo.searchdefinition.RankProfile;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.expressiontransforms.OnnxModelTransformer;
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
 * And generates an "onnx-model" configuration as if it was defined in the schema:
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

    public OnnxModelConfigGenerator(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        if (documentsOnly) return;
        for (RankProfile profile : rankProfileRegistry.rankProfilesOf(search)) {
            if (profile.getFirstPhaseRanking() != null) {
                process(profile.getFirstPhaseRanking().getRoot());
            }
            if (profile.getSecondPhaseRanking() != null) {
                process(profile.getSecondPhaseRanking().getRoot());
            }
            for (Map.Entry<String, RankProfile.RankingExpressionFunction> function : profile.getFunctions().entrySet()) {
                process(function.getValue().function().getBody().getRoot());
            }
            for (ReferenceNode feature : profile.getSummaryFeatures()) {
                process(feature);
            }
        }
    }

    private void process(ExpressionNode node) {
        if (node instanceof ReferenceNode) {
            process((ReferenceNode)node);
        } else if (node instanceof CompositeNode) {
            for (ExpressionNode child : ((CompositeNode) node).children()) {
                process(child);
            }
        }
    }

    private void process(ReferenceNode feature) {
        if (feature.getName().equals("onnxModel") || feature.getName().equals("onnx")) {
            if (feature.getArguments().size() > 0) {
                if (feature.getArguments().expressions().get(0) instanceof ConstantNode) {
                    ConstantNode node = (ConstantNode) feature.getArguments().expressions().get(0);
                    String path = OnnxModelTransformer.stripQuotes(node.sourceString());
                    String modelConfigName = OnnxModelTransformer.asValidIdentifier(path);

                    // Only add the configuration if the model can actually be found.
                    if ( ! OnnxModelInfo.modelExists(path, search.applicationPackage())) {
                        path = ApplicationPackage.MODELS_DIR.append(path).toString();
                        if ( ! OnnxModelInfo.modelExists(path, search.applicationPackage())) {
                            return;
                        }
                    }

                    OnnxModel onnxModel = search.onnxModels().get(modelConfigName);
                    if (onnxModel == null) {
                        onnxModel = new OnnxModel(modelConfigName, path);
                        search.onnxModels().add(onnxModel);
                    }
                }
            }
        }
    }

}
