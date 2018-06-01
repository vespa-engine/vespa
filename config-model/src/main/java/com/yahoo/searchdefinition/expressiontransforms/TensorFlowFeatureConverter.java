// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.expressiontransforms;

import com.yahoo.path.Path;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.searchdefinition.RankProfile;
import com.yahoo.searchlib.rankingexpression.integration.ml.ImportedModel;
import com.yahoo.searchlib.rankingexpression.integration.ml.TensorFlowImporter;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;

import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Replaces instances of the tensorflow(model-path, signature, output)
 * pseudofeature with the native Vespa ranking expression implementing
 * the same computation.
 *
 * @author bratseth
 */
public class TensorFlowFeatureConverter extends MLImportFeatureConverter {

    private final TensorFlowImporter tensorFlowImporter = new TensorFlowImporter();

    /** A cache of imported models indexed by model path. This avoids importing the same model multiple times. */
    private final Map<Path, ImportedModel> importedModels = new HashMap<>();

    @Override
    public ExpressionNode transform(ExpressionNode node, RankProfileTransformContext context) {
        if (node instanceof ReferenceNode)
            return transformFeature((ReferenceNode) node, context);
        else if (node instanceof CompositeNode)
            return super.transformChildren((CompositeNode) node, context);
        else
            return node;
    }

    private ExpressionNode transformFeature(ReferenceNode feature, RankProfileTransformContext context) {
        if ( ! feature.getName().equals("tensorflow")) return feature;

        try {
            ModelStore store = new ModelStore(context.rankProfile().getSearch().sourceApplication(), feature.getArguments());
            if ( ! store.hasStoredModel()) // not converted yet - access TensorFlow model files
                return transformFromTensorFlowModel(store, context.rankProfile(), context.queryProfiles());
            else
                return transformFromStoredModel(store, context.rankProfile());
        }
        catch (IllegalArgumentException | UncheckedIOException e) {
            throw new IllegalArgumentException("Could not use tensorflow model from " + feature, e);
        }
    }

    private ExpressionNode transformFromTensorFlowModel(ModelStore store,
                                                          RankProfile profile,
                                                          QueryProfileRegistry queryProfiles) {
        ImportedModel model = importedModels.computeIfAbsent(store.arguments().modelPath(),
                k -> tensorFlowImporter.importModel(store.arguments().modelName(),
                        store.modelDir()));
        return transformFromImportedModel(model, store, profile, queryProfiles);
    }

}
