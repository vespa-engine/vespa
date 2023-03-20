// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.expressiontransforms;

import ai.vespa.rankingexpression.importer.configmodelview.ImportedMlModels;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.schema.RankProfile;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.DoubleValue;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.transform.TransformContext;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Extends the transform context with rank profile information
 *
 * @author bratseth
 */
public class RankProfileTransformContext extends TransformContext {

    private final RankProfile rankProfile;
    private final QueryProfileRegistry queryProfiles;
    private final ImportedMlModels importedModels;
    private final Map<String, RankProfile.RankingExpressionFunction> inlineFunctions;
    private final Map<String, String> rankProperties = new LinkedHashMap<>();

    public RankProfileTransformContext(RankProfile rankProfile,
                                       QueryProfileRegistry queryProfiles,
                                       Map<Reference, TensorType> featureTypes,
                                       ImportedMlModels importedModels,
                                       Map<Reference, RankProfile.Constant> constants,
                                       Map<String, RankProfile.RankingExpressionFunction> inlineFunctions) {
        super(valuesOf(constants), rankProfile.typeContext(queryProfiles, featureTypes));
        this.rankProfile = rankProfile;
        this.queryProfiles = queryProfiles;
        this.importedModels = importedModels;
        this.inlineFunctions = inlineFunctions;
    }

    public RankProfile rankProfile() { return rankProfile; }
    public QueryProfileRegistry queryProfiles() { return queryProfiles; }
    public ImportedMlModels importedModels() { return importedModels; }
    public Map<String, RankProfile.RankingExpressionFunction> inlineFunctions() { return inlineFunctions; }
    public Map<String, String> rankProperties() { return rankProperties; }

    private static Map<String, Value> valuesOf(Map<Reference, RankProfile.Constant> constants) {
        return constants.values().stream()
                        .filter(constant -> constant.value().isPresent())
                        .collect(Collectors.toMap(constant -> constant.name().simpleArgument().get(),
                                                  constant -> asValue(constant.value().get())));
    }

    private static Value asValue(Tensor tensor) {
        if (tensor.type().rank() == 0)
            return DoubleValue.of(tensor.asDouble());
        else
            return TensorValue.of(tensor);
    }

}
