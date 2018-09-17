// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.expressiontransforms;

import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.searchdefinition.RankProfile;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.integration.ml.ImportedModels;
import com.yahoo.searchlib.rankingexpression.transform.TransformContext;

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
    private final ImportedModels importedModels;
    private final Map<String, RankProfile.RankingExpressionFunction> inlineMacros;
    private final Map<String, String> rankProperties = new HashMap<>();

    public RankProfileTransformContext(RankProfile rankProfile,
                                       QueryProfileRegistry queryProfiles,
                                       ImportedModels importedModels,
                                       Map<String, Value> constants,
                                       Map<String, RankProfile.RankingExpressionFunction> inlineMacros) {
        super(constants);
        this.rankProfile = rankProfile;
        this.queryProfiles = queryProfiles;
        this.importedModels = importedModels;
        this.inlineMacros = inlineMacros;
    }

    public RankProfile rankProfile() { return rankProfile; }
    public QueryProfileRegistry queryProfiles() { return queryProfiles; }
    public ImportedModels importedModels() { return importedModels; }
    public Map<String, RankProfile.RankingExpressionFunction> inlineMacros() { return inlineMacros; }
    public Map<String, String> rankProperties() { return rankProperties; }

}
