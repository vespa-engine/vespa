// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.evaluation;

import ai.vespa.models.evaluation.Model;
import com.yahoo.searchlib.rankingexpression.ExpressionFunction;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.vespa.config.search.RankProfilesConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts RankProfilesConfig instances to RankingExpressions for evaluation
 *
 * @author bratseth
 */
class RankProfilesConfigImporter {

    // TODO: Move to separate class ... or something
    static final Pattern expressionPattern =
            Pattern.compile("rankingExpression\\(([a-zA-Z0-9_]+)(@[a-f0-9]+\\.[a-f0-9]+)?\\)\\.rankingScript");

    /**
     * Returns a map of the models contained in this config, indexed on name.
     * The map is modifiable and owned by the caller.
     */
    public Map<String, Model> importFrom(RankProfilesConfig config) {
        Map<String, Model> models = new HashMap<>();
        for (RankProfilesConfig.Rankprofile profile : config.rankprofile()) {
            Model model = importProfile(profile);
            models.put(model.name(), model);
        }
        return models;
    }

    private Model importProfile(RankProfilesConfig.Rankprofile profile) {
        List<ExpressionFunction> functions = new ArrayList<>();
        List<ExpressionFunction> boundFunctions = new ArrayList<>();
        ExpressionFunction firstPhase = null;
        ExpressionFunction secondPhase = null;
        for (RankProfilesConfig.Rankprofile.Fef.Property property : profile.fef().property()) {
            Matcher expressionMatcher = expressionPattern.matcher(property.name());
            if ( expressionMatcher.matches()) {
                String name = expressionMatcher.group(1);
                String instance = expressionMatcher.group(2);
                List<String> arguments = new ArrayList<>(); // TODO: Arguments?
                RankingExpression expression = RankingExpression.from(property.value());

                if (instance == null)
                    functions.add(new ExpressionFunction(name, arguments, expression));
                else
                    boundFunctions.add(new ExpressionFunction(name + instance, arguments, expression));
            }
            else if (property.name().equals("vespa.rank.firstphase")) { // Include in addition to macros
                firstPhase = new ExpressionFunction("firstphase", new ArrayList<>(), RankingExpression.from(property.value()));
            }
            else if (property.name().equals("vespa.rank.secondphase")) { // Include in addition to macros
                secondPhase = new ExpressionFunction("secondphase", new ArrayList<>(), RankingExpression.from(property.value()));
            }
        }
        if (functionByName("firstphase", functions) == null && firstPhase != null) // may be already included, depending on body
            functions.add(firstPhase);
        if (functionByName("secondphase", functions) == null && secondPhase != null) // may be already included, depending on body
            functions.add(secondPhase);
        return new Model(profile.name(), functions, boundFunctions);
    }

    private ExpressionFunction functionByName(String name, List<ExpressionFunction> functions) {
        for (ExpressionFunction function : functions)
            if (function.getName().equals(name))
                return function;
        return null;
    }

}
