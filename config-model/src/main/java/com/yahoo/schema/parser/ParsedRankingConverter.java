// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.parser;

import com.yahoo.schema.RankProfile;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.RankType;

import java.util.List;

/**
 * Helper for converting ParsedRankProfile etc to RankProfile with settings
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
            RankProfile profile = createProfile(schema, parsed.name());
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
        parsed.getInherited().forEach(profile::inherit);
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
        parsed.getTermwiseLimit().ifPresent(profile::setTermwiseLimit);
        parsed.getPostFilterThreshold().ifPresent(profile::setPostFilterThreshold);
        parsed.getApproximateThreshold().ifPresent(profile::setApproximateThreshold);
        parsed.getTargetHitsMaxAdjustmentFactor().ifPresent(profile::setTargetHitsMaxAdjustmentFactor);
        parsed.getKeepRankCount().ifPresent(profile::setKeepRankCount);
        parsed.getMinHitsPerThread().ifPresent(profile::setMinHitsPerThread);
        parsed.getNumSearchPartitions().ifPresent(profile::setNumSearchPartitions);
        parsed.getNumThreadsPerSearch().ifPresent(profile::setNumThreadsPerSearch);
        parsed.getReRankCount().ifPresent(profile::setRerankCount);

        parsed.getMatchPhase().ifPresent(profile::setMatchPhase);
        parsed.getDiversity().ifPresent(profile::setDiversity);

        parsed.getFirstPhaseExpression().ifPresent(profile::setFirstPhaseRanking);
        parsed.getSecondPhaseExpression().ifPresent(profile::setSecondPhaseRanking);

        parsed.getGlobalPhaseExpression().ifPresent(profile::setGlobalPhaseRanking);
        parsed.getGlobalPhaseRerankCount().ifPresent(profile::setGlobalPhaseRerankCount);

        parsed.getMatchFeatures().forEach(profile::addMatchFeatures);
        parsed.getRankFeatures().forEach(profile::addRankFeatures);
        parsed.getSummaryFeatures().forEach(profile::addSummaryFeatures);
        parsed.getInheritedMatchFeatures().ifPresent(profile::setInheritedMatchFeatures);
        parsed.getInheritedSummaryFeatures().ifPresent(profile::setInheritedSummaryFeatures);
        if (parsed.getIgnoreDefaultRankFeatures())
            profile.setIgnoreDefaultRankFeatures(true);

        parsed.getMutateOperations().forEach(profile::addMutateOperation);
        parsed.getFieldsWithRankFilter().forEach
                                                ((fieldName, isFilter) -> profile.addRankSetting(fieldName, RankProfile.RankSetting.Type.PREFERBITVECTOR, isFilter));

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
