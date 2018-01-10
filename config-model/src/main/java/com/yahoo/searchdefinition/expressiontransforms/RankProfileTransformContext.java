package com.yahoo.searchdefinition.expressiontransforms;

import com.yahoo.searchdefinition.RankProfile;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.transform.TransformContext;

import java.util.Map;

/**
 * Extends the transform context with rank profile information
 *
 * @author bratseth
 */
public class RankProfileTransformContext extends TransformContext {

    private final RankProfile rankProfile;
    private final Map<String, RankProfile.Macro> inlineMacros;
    private final Map<String, String> rankPropertiesOutput;

    public RankProfileTransformContext(RankProfile rankProfile,
                                Map<String, Value> constants,
                                Map<String, RankProfile.Macro> inlineMacros,
                                Map<String, String> rankPropertiesOutput) {
        super(constants);
        this.rankProfile = rankProfile;
        this.inlineMacros = inlineMacros;
        this.rankPropertiesOutput = rankPropertiesOutput;
    }

    public RankProfile rankProfile() { return rankProfile; }
    public Map<String, RankProfile.Macro> inlineMacros() { return inlineMacros; }
    public Map<String, String> rankPropertiesOutput() { return rankPropertiesOutput; }

}
