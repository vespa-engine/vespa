// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.parser;

import com.yahoo.schema.OnnxModel;
import com.yahoo.schema.RankProfile;
import com.yahoo.schema.RankProfile.MatchPhaseSettings;
import com.yahoo.schema.RankProfile.MutateOperation;
import com.yahoo.searchlib.rankingexpression.FeatureList;
import com.yahoo.searchlib.rankingexpression.Reference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
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
    private Double postFilterThreshold = null;
    private Double approximateThreshold = null;
    private final List<FeatureList> matchFeatures = new ArrayList<>();
    private final List<FeatureList> rankFeatures = new ArrayList<>();
    private final List<FeatureList> summaryFeatures = new ArrayList<>();
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
    private final Map<String, Boolean> fieldsRankFilter = new LinkedHashMap<>();
    private final Map<String, Integer> fieldsRankWeight = new LinkedHashMap<>();
    private final Map<String, ParsedRankFunction> functions = new LinkedHashMap<>();
    private final Map<String, String> fieldsRankType = new LinkedHashMap<>();
    private final Map<String, List<String>> rankProperties = new LinkedHashMap<>();
    private final Map<Reference, RankProfile.Constant> constants = new LinkedHashMap<>();
    private final Map<Reference, RankProfile.Input> inputs = new LinkedHashMap<>();
    private final List<OnnxModel> onnxModels = new ArrayList<>();

    ParsedRankProfile(String name) {
        super(name, "rank-profile");
    }

    boolean getIgnoreDefaultRankFeatures() { return this.ignoreDefaultRankFeatures; }
    Optional<Double> getRankScoreDropLimit() { return Optional.ofNullable(this.rankScoreDropLimit); }
    Optional<Double> getTermwiseLimit() { return Optional.ofNullable(this.termwiseLimit); }
    Optional<Double> getPostFilterThreshold() { return Optional.ofNullable(this.postFilterThreshold); }
    Optional<Double> getApproximateThreshold() { return Optional.ofNullable(this.approximateThreshold); }
    List<FeatureList> getMatchFeatures() { return List.copyOf(this.matchFeatures); }
    List<FeatureList> getRankFeatures() { return List.copyOf(this.rankFeatures); }
    List<FeatureList> getSummaryFeatures() { return List.copyOf(this.summaryFeatures); }
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

    Map<String, Boolean> getFieldsWithRankFilter() { return Collections.unmodifiableMap(fieldsRankFilter); }
    Map<String, Integer> getFieldsWithRankWeight() { return Collections.unmodifiableMap(fieldsRankWeight); }
    Map<String, String> getFieldsWithRankType() { return Collections.unmodifiableMap(fieldsRankType); }
    Map<String, List<String>> getRankProperties() { return Collections.unmodifiableMap(rankProperties); }
    Map<Reference, RankProfile.Constant> getConstants() { return Collections.unmodifiableMap(constants); }
    Map<Reference, RankProfile.Input> getInputs() { return Collections.unmodifiableMap(inputs); }
    List<OnnxModel> getOnnxModels() { return List.copyOf(onnxModels); }

    Optional<String> getInheritedSummaryFeatures() { return Optional.ofNullable(this.inheritedSummaryFeatures); }
    Optional<String> getSecondPhaseExpression() { return Optional.ofNullable(this.secondPhaseExpression); }
    Optional<Boolean> isStrict() { return Optional.ofNullable(this.strict); }

    void addSummaryFeatures(FeatureList features) { this.summaryFeatures.add(features); }
    void addMatchFeatures(FeatureList features) { this.matchFeatures.add(features); }
    void addRankFeatures(FeatureList features) { this.rankFeatures.add(features); }

    void inherit(String other) { inherited.add(other); }

    void setInheritedSummaryFeatures(String other) {
        verifyThat(inheritedSummaryFeatures == null, "already inherits summary-features");
        this.inheritedSummaryFeatures = other;
    }

    void add(RankProfile.Constant constant) {
        verifyThat(! constants.containsKey(constant.name()), "already has constant", constant.name());
        constants.put(constant.name(), constant);
    }

    void addInput(Reference name, RankProfile.Input input) {
        verifyThat(! inputs.containsKey(name), "already has input", name);
        inputs.put(name, input);
    }

    void add(OnnxModel model) {
        onnxModels.add(model);
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

    ParsedRankFunction addOrReplaceFunction(ParsedRankFunction func) {
        // allowed with warning
        // verifyThat(! functions.containsKey(func.name()), "already has function", func.name());
        return functions.put(func.name(), func);
    }

    void addMutateOperation(MutateOperation.Phase phase, String attrName, String operation) {
        mutateOperations.add(new MutateOperation(phase, attrName, operation));
    }

    void addRankProperty(String key, String value) {
        List<String> values = rankProperties.computeIfAbsent(key, k -> new ArrayList<String>());
        values.add(value);
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

    void setPostFilterThreshold(double threshold) {
        verifyThat(postFilterThreshold == null, "already has post-filter-threshold");
        this.postFilterThreshold = threshold;
    }

    void setApproximateThreshold(double threshold) {
        verifyThat(approximateThreshold == null, "already has approximate-threshold");
        this.approximateThreshold = threshold;
    }

}
