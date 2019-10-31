// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.expressiontransforms;

import ai.vespa.rankingexpression.importer.configmodelview.ImportedMlModels;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.searchdefinition.RankProfile;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.transform.TransformContext;
import com.yahoo.tensor.TensorType;

import java.util.HashMap;
import java.util.Map;

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
    private final Map<String, String> rankProperties = new HashMap<>();

    public RankProfileTransformContext(RankProfile rankProfile,
                                       QueryProfileRegistry queryProfiles,
                                       Map<Reference, TensorType> featureTypes,
                                       ImportedMlModels importedModels,
                                       Map<String, Value> constants,
                                       Map<String, RankProfile.RankingExpressionFunction> inlineFunctions) {
        super(constants, rankProfile.typeContext(queryProfiles, featureTypes));
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

}
