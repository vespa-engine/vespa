// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.parser;

import com.yahoo.schema.RankProfile;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.RankType;

import java.util.List;

/**
 * Helper for converting ParsedRankProfile etc. to RankProfile with settings
 *
 * @author arnej27959
 */
public class ParsedRankingConverter {

    private final RankProfileRegistry rankProfileRegistry;

    // for unit test
    ParsedRankingConverter() {
        this(new RankProfileRegistry());
    }

    public ParsedRankingConverter(RankProfileRegistry rankProfileRegistry) {
        this.rankProfileRegistry = rankProfileRegistry;
    }

    void convertRankProfile(Schema schema, ParsedRankProfile parsed) {
        try {
            parsed.outer().ifPresent(parent -> {
                if ( ! parsed.getInherited().contains(parent.name()))
                    throw new IllegalArgumentException("Inner profile '" + parsed.name() + "' must inherit '" +
                                                       parent.name() + "'");
            });
            RankProfile profile = createProfile(schema, parsed.fullName());
            populateFrom(parsed, profile);
            rankProfileRegistry.add(profile);
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("In " + parsed, e);
        }
    }

    private RankProfile createProfile(Schema schema, String name) {
        if (name.equals("default")) return rankProfileRegistry.get(schema, "default");
        return new RankProfile(name, schema, rankProfileRegistry);
    }

    private void populateFrom(ParsedRankProfile parsed, RankProfile profile) {
        for (var inherited : parsed.getInherited()) {
            String namePrefix = parsed.outer().map(p -> p.namespacePrefix()).orElse("");
            profile.inherit(namePrefix + inherited);
        }
        parsed.isStrict().ifPresent(profile::setStrict);
        parsed.isUseSignificanceModel().ifPresent(profile::setUseSignificanceModel);
        parsed.getConstants().values().forEach(profile::add);
        parsed.getOnnxModels().forEach(profile::add);
        parsed.getInputs().forEach(profile::addInput);

        for (var func : parsed.getFunctions()) {
            String name = func.name();
            List<String> parameters = func.getParameters();
            String expression = func.getExpression();
            boolean inline = func.getInline();
            profile.addFunction(name, parameters, expression, inline);
        }

        parsed.getRankScoreDropLimit().ifPresent(profile::setRankScoreDropLimit);
        parsed.getSecondPhaseRankScoreDropLimit().ifPresent(profile::setSecondPhaseRankScoreDropLimit);
        parsed.getGlobalPhaseRankScoreDropLimit().ifPresent(profile::setGlobalPhaseRankScoreDropLimit);
        parsed.getTermwiseLimit().ifPresent(profile::setTermwiseLimit);
        parsed.getPostFilterThreshold().ifPresent(profile::setPostFilterThreshold);
        parsed.getApproximateThreshold().ifPresent(profile::setApproximateThreshold);
        parsed.getFilterFirstThreshold().ifPresent(profile::setFilterFirstThreshold);
        parsed.getFilterFirstExploration().ifPresent(profile::setFilterFirstExploration);
        parsed.getExplorationSlack().ifPresent(profile::setExplorationSlack);
        parsed.getPrefetchTensors().ifPresent(profile::setPrefetchTensors);
        parsed.getTargetHitsMaxAdjustmentFactor().ifPresent(profile::setTargetHitsMaxAdjustmentFactor);
        parsed.getWeakandStopwordLimit().ifPresent(profile::setWeakandStopwordLimit);
        parsed.getWeakandAllowDropAll().ifPresent(profile::setWeakandAllowDropAll);
        parsed.getWeakandAdjustTarget().ifPresent(profile::setWeakandAdjustTarget);
        parsed.getFilterThreshold().ifPresent(profile::setFilterThreshold);
        parsed.getKeepRankCount().ifPresent(profile::setKeepRankCount);
        parsed.getTotalKeepRankCount().ifPresent(profile::setTotalKeepRankCount);
        parsed.getMinHitsPerThread().ifPresent(profile::setMinHitsPerThread);
        parsed.getNumSearchPartitions().ifPresent(profile::setNumSearchPartitions);
        parsed.getNumThreadsPerSearch().ifPresent(profile::setNumThreadsPerSearch);
        parsed.getRerankCount().ifPresent(profile::setRerankCount);
        parsed.getTotalRerankCount().ifPresent(profile::setTotalRerankCount);

        parsed.getMatchPhase().ifPresent(profile::setMatchPhase);
        parsed.getDiversity().ifPresent(profile::setDiversity);

        parsed.getFirstPhaseExpression().ifPresent(profile::setFirstPhaseRanking);
        parsed.getSecondPhaseExpression().ifPresent(profile::setSecondPhaseRanking);

        parsed.getGlobalPhaseExpression().ifPresent(profile::setGlobalPhaseRanking);
        parsed.getGlobalPhaseRerankCount().ifPresent(profile::setGlobalPhaseRerankCount);

        parsed.getMatchFeatures().forEach(profile::addMatchFeatures);
        parsed.getRankFeatures().forEach(profile::addRankFeatures);
        parsed.getSummaryFeatures().forEach(profile::addSummaryFeatures);
        parsed.getInheritedMatchFeatures().forEach(profile::addInheritedMatchFeatures);
        parsed.getInheritedSummaryFeatures().forEach(profile::addInheritedSummaryFeatures);
        if (parsed.getIgnoreDefaultRankFeatures())
            profile.setIgnoreDefaultRankFeatures(true);

        parsed.getMutateOperations().forEach(profile::addMutateOperation);
        parsed.getFieldsWithRankFilter().forEach
                                                ((fieldName, isFilter) -> profile.addRankSetting(fieldName, RankProfile.RankSetting.Type.PREFERBITVECTOR, isFilter));

        profile.setExplicitFieldRankFilterThresholds(parsed.getFieldsWithRankFilterThreshold());
        profile.setExplicitFieldRankElementGaps(parsed.getFieldsWithElementGap());

        parsed.getFieldsWithRankWeight().forEach
                                                ((fieldName, weight) -> profile.addRankSetting(fieldName, RankProfile.RankSetting.Type.WEIGHT, weight));

        parsed.getFieldsWithRankType().forEach
                                              ((fieldName, rankType) -> profile.addRankSetting(fieldName, RankProfile.RankSetting.Type.RANKTYPE, RankType.fromString(rankType)));

        parsed.getRankProperties().forEach
                                          ((key, values) -> {
                                              for (String value : values) profile.addRankProperty(key, value);
                                          });
    }

}
