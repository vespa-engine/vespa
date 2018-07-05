// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.evaluation.config;

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
public class RankProfilesConfigImporter {

    private static final Pattern expressionPattern = Pattern.compile("rankingExpression\\(([a-zA-Z0-9_]+)\\)\\.rankingScript");

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
        System.out.println("Importing " + profile.name());
        List<ExpressionFunction> functions = new ArrayList<>();
        for (RankProfilesConfig.Rankprofile.Fef.Property property : profile.fef().property()) {
            Matcher expressionMatcher = expressionPattern.matcher(property.name());
            if ( ! expressionMatcher.matches()) continue;

            System.out.println("    Importing " + expressionMatcher.group(0));

            String name = expressionMatcher.group(0);
            List<String> arguments = new ArrayList<>();
            RankingExpression expression = RankingExpression.from(property.value());
            functions.add(new ExpressionFunction(name, arguments, expression));
        }
        return new Model(profile.name(), functions);
    }

}
