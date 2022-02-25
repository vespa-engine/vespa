// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.parser;

import com.yahoo.searchdefinition.RankProfile.MatchPhaseSettings;
import com.yahoo.searchdefinition.RankProfile.MutateOperation;
import com.yahoo.searchlib.rankingexpression.FeatureList;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * This class holds the extracted information after parsing a
 * rank-profile block in a schema (.sd) file, using simple data
 * structures as far as possible.  Do not put advanced logic here!
 *
 * @author arnej27959
 */
class ParsedRankProfile extends ParsedBlock {

    private boolean ignoreDefaultRankFeatures = false;
    private Double rankScoreDropLimit = null;
    private Double termwiseLimit = null;
    private FeatureList matchFeatures = null;
    private FeatureList rankFeatures = null;
    private FeatureList summaryFeatures = null;
    private Integer keepRankCount = null;
    private Integer minHitsPerThread = null;
    private Integer numSearchPartitions = null;
    private Integer numThreadsPerSearch = null;
    private Integer reRankCount = null;
    private MatchPhaseSettings matchPhaseSettings = null;
    private String firstPhaseExpression = null;
    private String inheritedSummaryFeatures = null;
    private String inheritedMatchFeatures = null;
    private String secondPhaseExpression = null;
    private Boolean strict = null;
    private final List<MutateOperation> mutateOperations = new ArrayList<>();
    private final List<String> inherited = new ArrayList<>();
    private final Map<String, Boolean> fieldsRankFilter = new HashMap<>();
    private final Map<String, Integer> fieldsRankWeight = new HashMap<>();
    private final Map<String, ParsedRankFunction> functions = new HashMap<>();
    private final Map<String, String> fieldsRankType = new HashMap<>();
    private final Map<String, String> rankProperties =  new HashMap<>();
    private final Map<String, Value> constants = new HashMap<>();

    ParsedRankProfile(String name) {
        super(name, "rank-profile");
    }

    boolean getIgnoreDefaultRankFeatures() { return this.ignoreDefaultRankFeatures; }
    Optional<Double> getRankScoreDropLimit() { return Optional.ofNullable(this.rankScoreDropLimit); }
    Optional<Double> getTermwiseLimit() { return Optional.ofNullable(this.termwiseLimit); }
    Optional<FeatureList> getMatchFeatures() { return Optional.ofNullable(this.matchFeatures); }
    Optional<FeatureList> getRankFeatures() { return Optional.ofNullable(this.rankFeatures); }
    Optional<FeatureList> getSummaryFeatures() { return Optional.ofNullable(this.summaryFeatures); }
    Optional<Integer> getKeepRankCount() { return Optional.ofNullable(this.keepRankCount); }
    Optional<Integer> getMinHitsPerThread() { return Optional.ofNullable(this.minHitsPerThread); }
    Optional<Integer> getNumSearchPartitions() { return Optional.ofNullable(this.numSearchPartitions); }
    Optional<Integer> getNumThreadsPerSearch() { return Optional.ofNullable(this.numThreadsPerSearch); }
    Optional<Integer> getReRankCount() { return Optional.ofNullable(this.reRankCount); }
    Optional<MatchPhaseSettings> getMatchPhaseSettings() { return Optional.ofNullable(this.matchPhaseSettings); }
    Optional<String> getFirstPhaseExpression() { return Optional.ofNullable(this.firstPhaseExpression); }
    Optional<String> getInheritedMatchFeatures() { return Optional.ofNullable(this.inheritedMatchFeatures); }
    List<ParsedRankFunction> getFunctions() { return List.copyOf(functions.values()); }
    List<MutateOperation> getMutateOperations() { return List.copyOf(mutateOperations); }
    List<String> getInherited() { return List.copyOf(inherited); }
    Map<String, Boolean> getFieldsWithRankFilter() { return Map.copyOf(fieldsRankFilter); }
    Map<String, Integer> getFieldsWithRankWeight() { return Map.copyOf(fieldsRankWeight); }
    Map<String, String> getFieldsWithRankType() { return Map.copyOf(fieldsRankType); }
    Map<String, String> getRankProperties() { return Map.copyOf(rankProperties); }
    Map<String, Value> getConstants() { return Map.copyOf(constants); }
    Optional<String> getInheritedSummaryFeatures() { return Optional.ofNullable(this.inheritedSummaryFeatures); }
    Optional<String> getSecondPhaseExpression() { return Optional.ofNullable(this.secondPhaseExpression); }
    Optional<Boolean> isStrict() { return Optional.ofNullable(this.strict); }

    void addSummaryFeatures(FeatureList features) {
        verifyThat(summaryFeatures == null, "already has summary-features");
        this.summaryFeatures = features;
    }

    void addMatchFeatures(FeatureList features) {
        verifyThat(matchFeatures == null, "already has match-features");
        this.matchFeatures = features;
    }

    void addRankFeatures(FeatureList features) {
        verifyThat(rankFeatures == null, "already has rank-features");
        this.rankFeatures = features;
    }

    void inherit(String other) { inherited.add(other); }

    void setInheritedSummaryFeatures(String other) {
        verifyThat(inheritedSummaryFeatures == null, "already inherits summary-features");
        this.inheritedSummaryFeatures = other;
    }

    void addConstant(String name, Value value) {
        verifyThat(! constants.containsKey(name), "already has constant", name);
        constants.put(name, value);
    }

    void addConstantTensor(String name, TensorValue value) {
        verifyThat(! constants.containsKey(name), "already has constant", name);
        constants.put(name, value);
    }

    void addFieldRankFilter(String field, boolean filter) {
        fieldsRankFilter.put(field, filter);
    }

    void addFieldRankType(String field, String type) {
        verifyThat(! fieldsRankType.containsKey(field), "already has rank type for field", field);
        fieldsRankType.put(field, type);
    }

    void addFieldRankWeight(String field, int weight) {
        verifyThat(! fieldsRankType.containsKey(field), "already has weight for field", field);
        fieldsRankWeight.put(field, weight);
    }

    void addFunction(ParsedRankFunction func) {
        verifyThat(! functions.containsKey(func.name()), "already has function", func.name());
        functions.put(func.name(), func);
    }

    void addMutateOperation(MutateOperation.Phase phase, String attrName, String operation) {
        mutateOperations.add(new MutateOperation(phase, attrName, operation));
    }

    void addRankProperty(String key, String value) {
        verifyThat(! rankProperties.containsKey(key), "already has value for rank property", key);
        rankProperties.put(key, value);
    }

    void setFirstPhaseRanking(String expression) {
        verifyThat(firstPhaseExpression == null, "already has first-phase expression");
        this.firstPhaseExpression = expression;
    }

    void setIgnoreDefaultRankFeatures(boolean value) {
        this.ignoreDefaultRankFeatures = value;
    }

    void setInheritedMatchFeatures(String other) {
        this.inheritedMatchFeatures = other;
    }

    void setKeepRankCount(int count) {
        verifyThat(keepRankCount == null, "already has rerank-count");
        this.keepRankCount = count;
    }

    void setMatchPhaseSettings(MatchPhaseSettings settings) {
        verifyThat(matchPhaseSettings == null, "already has match-phase");
        this.matchPhaseSettings = settings;
    }

    void setMinHitsPerThread(int minHits) {
        verifyThat(minHitsPerThread == null, "already has min-hits-per-thread");
        this.minHitsPerThread = minHits;
    }

    void setNumSearchPartitions(int numParts) {
        verifyThat(numSearchPartitions == null, "already has num-search-partitions");
        this.numSearchPartitions = numParts;
    }

    void setNumThreadsPerSearch(int threads) {
        verifyThat(numThreadsPerSearch == null, "already has num-threads-per-search");
        this.numThreadsPerSearch = threads;
    }

    void setRankScoreDropLimit(double limit) {
        verifyThat(rankScoreDropLimit == null, "already has rank-score-drop-limit");
        this.rankScoreDropLimit = limit;
    }

    void setRerankCount(int count) {
        verifyThat(reRankCount == null, "already has rerank-count");
        this.reRankCount = count;
    }

    void setSecondPhaseRanking(String expression) {
        verifyThat(secondPhaseExpression == null, "already has second-phase expression");
        this.secondPhaseExpression = expression;
    }

    void setStrict(boolean strict) {
        verifyThat(this.strict == null, "already has strict");
        this.strict = strict;
    }
  
    void setTermwiseLimit(double limit) {
        verifyThat(termwiseLimit == null, "already has termwise-limit");
        this.termwiseLimit = limit;
    }
  
    
}
