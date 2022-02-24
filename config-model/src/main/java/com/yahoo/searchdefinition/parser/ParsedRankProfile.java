// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.parser;

import com.yahoo.searchdefinition.RankProfile.MatchPhaseSettings;
import com.yahoo.searchdefinition.RankProfile.MutateOperation;
import com.yahoo.searchlib.rankingexpression.FeatureList;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;

/**
 * This class holds the extracted information after parsing a
 * rank-profile block in a schema (.sd) file, using simple data
 * structures as far as possible.  Do not put advanced logic here!
 * @author arnej27959
 **/
class ParsedRankProfile extends ParsedBlock {

    ParsedRankProfile(String name) {
        super(name, "rank-profile");
    }

    void addSummaryFeatures(FeatureList features) {}
    void addMatchFeatures(FeatureList features) {}
    void addRankFeatures(FeatureList features) {}

    void inherit(String other) {}
    void setInheritedSummaryFeatures(String other) {}

    void addFieldRankType(String field, String type) {}
    void addFieldRankWeight(String field, int weight) {}
    void addFieldRankFilter(String field, boolean filter) {}
    void setSecondPhaseRanking(String expression) {}
    void setRerankCount(int count) {}
    void setFirstPhaseRanking(String expression) {}
    void setKeepRankCount(int count) {}
    void setRankScoreDropLimit(double limit) {}
    void setMatchPhaseSettings(MatchPhaseSettings settings) {}
    void addFunction(ParsedRankFunction func) {}
    void addMutateOperation(MutateOperation.Phase phase, String attrName, String operation) {}
    void setIgnoreDefaultRankFeatures(boolean ignore) {}
    void setNumThreadsPerSearch(int threads) {}
    void setMinHitsPerThread(int minHits) {}
    void setNumSearchPartitions(int numParts) {}
    void setTermwiseLimit(double limit) {}
    void setInheritedMatchFeatures(String other) {}
    void addRankProperty(String key, String value) {}
    void addConstant(String name, Value value) {}
    void addConstantTensor(String name, TensorValue value) {}
}
