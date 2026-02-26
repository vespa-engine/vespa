// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.parser;

import com.yahoo.schema.OnnxModel;
import com.yahoo.schema.RankProfile;
import com.yahoo.schema.RankProfile.MatchPhaseSettings;
import com.yahoo.schema.RankProfile.DiversitySettings;
import com.yahoo.schema.RankProfile.MutateOperation;
import com.yahoo.search.query.ranking.ElementGap;
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
public class ParsedRankProfile extends ParsedBlock {

    /** The profile enclosing this, or null if it is not an inner profile */
    private final ParsedRankProfile outer;
    private boolean ignoreDefaultRankFeatures = false;
    private Double rankScoreDropLimit = null;
    private Double secondPhaseRankScoreDropLimit = null;
    private Double globalPhaseRankScoreDropLimit = null;
    private Double termwiseLimit = null;
    private Double postFilterThreshold = null;
    private Double approximateThreshold = null;
    private Double filterFirstThreshold = null;
    private Double filterFirstExploration = null;
    private Double explorationSlack = null;
    private Boolean prefetchTensors = null;
    private Double targetHitsMaxAdjustmentFactor = null;
    private final List<FeatureList> matchFeatures = new ArrayList<>();
    private final List<FeatureList> rankFeatures = new ArrayList<>();
    private final List<FeatureList> summaryFeatures = new ArrayList<>();
    private Integer keepRankCount = null;
    private Integer totalKeepRankCount = null;
    private Integer minHitsPerThread = null;
    private Integer numSearchPartitions = null;
    private Integer numThreadsPerSearch = null;
    private Integer rerankCount = null;
    private Integer totalRerankCount = null;
    private MatchPhaseSettings matchPhase = null;
    private DiversitySettings diversity = null;
    private String firstPhaseExpression     = null;
    private final List<String> inheritedSummaryFeatures = new ArrayList<>();
    private final List<String> inheritedMatchFeatures   = new ArrayList<>();
    private String secondPhaseExpression    = null;
    private Boolean strict = null;
    private Boolean useSignificanceModel = null;
    private Double weakandStopwordLimit = null;
    private Boolean weakandAllowDropAll = null;
    private Double weakandAdjustTarget = null;
    private Double filterThreshold = null;
    private final List<MutateOperation> mutateOperations = new ArrayList<>();
    private final List<String> inherited = new ArrayList<>();
    private final Map<String, Boolean> fieldsRankFilter = new LinkedHashMap<>();
    private final Map<String, Double> fieldsRankFilterThreshold = new LinkedHashMap<>();
    private final Map<String, ElementGap> fieldsRankElementGap = new LinkedHashMap<>();
    private final Map<String, Integer> fieldsRankWeight = new LinkedHashMap<>();
    private final Map<String, ParsedRankFunction> functions = new LinkedHashMap<>();
    private final Map<String, String> fieldsRankType = new LinkedHashMap<>();
    private final Map<String, List<String>> rankProperties = new LinkedHashMap<>();
    private final Map<Reference, RankProfile.Constant> constants = new LinkedHashMap<>();
    private final Map<Reference, RankProfile.Input> inputs = new LinkedHashMap<>();
    private final List<OnnxModel> onnxModels = new ArrayList<>();
    private Integer globalPhaseRerankCount = null;
    private String globalPhaseExpression = null;

    public ParsedRankProfile(String name, ParsedRankProfile outer) {
        super(name, "rank-profile");
        this.outer = outer;
    }

    String namespacePrefix() {
        return outer().map(parent -> parent.fullName() + ".").orElse("");
    }

    String fullName() {
        return namespacePrefix() + name();
    }

    Optional<ParsedRankProfile> outer() { return Optional.ofNullable(outer); }
    boolean getIgnoreDefaultRankFeatures() { return this.ignoreDefaultRankFeatures; }
    Optional<Double> getRankScoreDropLimit() { return Optional.ofNullable(this.rankScoreDropLimit); }
    Optional<Double> getSecondPhaseRankScoreDropLimit() { return Optional.ofNullable(this.secondPhaseRankScoreDropLimit); }
    Optional<Double> getTermwiseLimit() { return Optional.ofNullable(this.termwiseLimit); }
    Optional<Double> getPostFilterThreshold() { return Optional.ofNullable(this.postFilterThreshold); }
    Optional<Double> getApproximateThreshold() { return Optional.ofNullable(this.approximateThreshold); }
    Optional<Double> getFilterFirstThreshold() { return Optional.ofNullable(this.filterFirstThreshold); }
    Optional<Double> getFilterFirstExploration() { return Optional.ofNullable(this.filterFirstExploration); }
    Optional<Double> getExplorationSlack() { return Optional.ofNullable(this.explorationSlack); }
    Optional<Boolean> getPrefetchTensors() { return Optional.ofNullable(this.prefetchTensors); }
    Optional<Double> getTargetHitsMaxAdjustmentFactor() { return Optional.ofNullable(this.targetHitsMaxAdjustmentFactor); }
    List<FeatureList> getMatchFeatures() { return List.copyOf(this.matchFeatures); }
    List<FeatureList> getRankFeatures() { return List.copyOf(this.rankFeatures); }
    List<FeatureList> getSummaryFeatures() { return List.copyOf(this.summaryFeatures); }
    Optional<Integer> getKeepRankCount() { return Optional.ofNullable(this.keepRankCount); }
    Optional<Integer> getTotalKeepRankCount() { return Optional.ofNullable(this.totalKeepRankCount); }
    Optional<Integer> getMinHitsPerThread() { return Optional.ofNullable(this.minHitsPerThread); }
    Optional<Integer> getNumSearchPartitions() { return Optional.ofNullable(this.numSearchPartitions); }
    Optional<Integer> getNumThreadsPerSearch() { return Optional.ofNullable(this.numThreadsPerSearch); }
    Optional<Integer> getRerankCount() { return Optional.ofNullable(this.rerankCount); }
    Optional<Integer> getTotalRerankCount() { return Optional.ofNullable(this.totalRerankCount); }
    Optional<MatchPhaseSettings> getMatchPhase() { return Optional.ofNullable(this.matchPhase); }
    Optional<DiversitySettings> getDiversity() { return Optional.ofNullable(this.diversity); }
    Optional<String> getFirstPhaseExpression() { return Optional.ofNullable(this.firstPhaseExpression); }
    List<String> getInheritedMatchFeatures() { return List.copyOf(this.inheritedMatchFeatures); }
    List<ParsedRankFunction> getFunctions() { return List.copyOf(functions.values()); }
    List<MutateOperation> getMutateOperations() { return List.copyOf(mutateOperations); }
    List<String> getInherited() { return List.copyOf(inherited); }
    Optional<Integer> getGlobalPhaseRerankCount() { return Optional.ofNullable(this.globalPhaseRerankCount); }
    Optional<Double> getGlobalPhaseRankScoreDropLimit() { return Optional.ofNullable(this.globalPhaseRankScoreDropLimit); }
    Optional<String> getGlobalPhaseExpression() { return Optional.ofNullable(this.globalPhaseExpression); }

    Map<String, Boolean> getFieldsWithRankFilter() { return Collections.unmodifiableMap(fieldsRankFilter); }
    Map<String, Double> getFieldsWithRankFilterThreshold() { return Collections.unmodifiableMap(fieldsRankFilterThreshold); }
    Map<String, ElementGap> getFieldsWithElementGap() { return Collections.unmodifiableMap(fieldsRankElementGap); }
    Map<String, Integer> getFieldsWithRankWeight() { return Collections.unmodifiableMap(fieldsRankWeight); }
    Map<String, String> getFieldsWithRankType() { return Collections.unmodifiableMap(fieldsRankType); }
    Map<String, List<String>> getRankProperties() { return Collections.unmodifiableMap(rankProperties); }
    Map<Reference, RankProfile.Constant> getConstants() { return Collections.unmodifiableMap(constants); }
    Map<Reference, RankProfile.Input> getInputs() { return Collections.unmodifiableMap(inputs); }
    List<OnnxModel> getOnnxModels() { return List.copyOf(onnxModels); }

    List<String> getInheritedSummaryFeatures() { return List.copyOf(this.inheritedSummaryFeatures); }
    Optional<String> getSecondPhaseExpression() { return Optional.ofNullable(this.secondPhaseExpression); }
    Optional<Boolean> isStrict() { return Optional.ofNullable(this.strict); }

    Optional<Boolean> isUseSignificanceModel() { return Optional.ofNullable(this.useSignificanceModel); }

    Optional<Double> getWeakandStopwordLimit() { return Optional.ofNullable(this.weakandStopwordLimit); }
    Optional<Boolean> getWeakandAllowDropAll() { return Optional.ofNullable(this.weakandAllowDropAll); }
    Optional<Double> getWeakandAdjustTarget() { return Optional.ofNullable(this.weakandAdjustTarget); }
    Optional<Double> getFilterThreshold() { return Optional.ofNullable(this.filterThreshold); }

    public void addSummaryFeatures(FeatureList features) { this.summaryFeatures.add(features); }
    public void addMatchFeatures(FeatureList features) { this.matchFeatures.add(features); }
    public void addRankFeatures(FeatureList features) { this.rankFeatures.add(features); }

    public void inherit(String other) { inherited.add(other); }

    public void setInheritedSummaryFeatures(List<String> others) {
        this.inheritedSummaryFeatures.addAll(others);
    }

    public void add(RankProfile.Constant constant) {
        verifyThat(! constants.containsKey(constant.name()), "already has constant", constant.name());
        constants.put(constant.name(), constant);
    }

    public void addInput(Reference name, RankProfile.Input input) {
        verifyThat(! inputs.containsKey(name), "already has input", name);
        inputs.put(name, input);
    }

    public void add(OnnxModel model) {
        onnxModels.add(model);
    }

    public void addFieldRankFilter(String field, boolean filter) {
        fieldsRankFilter.put(field, filter);
    }

    public void addFieldRankFilterThreshold(String field, double filterThreshold) {
        verifyThat(!fieldsRankFilterThreshold.containsKey(field), "already has rank filter-threshold for field", field);
        verifyThat(filterThreshold >= 0.0 && filterThreshold <= 1.0, "must be a value in range [0, 1]", field);
        fieldsRankFilterThreshold.put(field, filterThreshold);
    }

    public void addFieldRankElementGap(String field, ElementGap elementGap) {
        fieldsRankElementGap.put(field, elementGap);
    }

    public void addFieldRankType(String field, String type) {
        verifyThat(! fieldsRankType.containsKey(field), "already has rank type for field", field);
        fieldsRankType.put(field, type);
    }

    public void addFieldRankWeight(String field, int weight) {
        verifyThat(! fieldsRankType.containsKey(field), "already has weight for field", field);
        fieldsRankWeight.put(field, weight);
    }

    public ParsedRankFunction addOrReplaceFunction(ParsedRankFunction func) {
        // allowed with warning
        // verifyThat(! functions.containsKey(func.name()), "already has function", func.name());
        return functions.put(func.name(), func);
    }

    public void addMutateOperation(MutateOperation.Phase phase, String attrName, String operation) {
        mutateOperations.add(new MutateOperation(phase, attrName, operation));
    }

    public void addRankProperty(String key, String value) {
        List<String> values = rankProperties.computeIfAbsent(key, k -> new ArrayList<String>());
        values.add(value);
    }

    public void setFirstPhaseRanking(String expression) {
        verifyThat(firstPhaseExpression == null, "already has first-phase expression");
        this.firstPhaseExpression = expression;
    }

    public void setIgnoreDefaultRankFeatures(boolean value) {
        this.ignoreDefaultRankFeatures = value;
    }

    public void setInheritedMatchFeatures(List<String> others) {
        this.inheritedMatchFeatures.addAll(others);
    }

    public void setKeepRankCount(int count) {
        verifyThat(keepRankCount == null, "already has keep-rank-count");
        this.keepRankCount = count;
    }

    public void setTotalKeepRankCount(int count) {
        verifyThat(totalKeepRankCount == null, "already has total-keep-rank-count");
        this.totalKeepRankCount = count;
    }

    public void setMatchPhase(MatchPhaseSettings settings) {
        verifyThat(matchPhase == null, "already has match-phase");
        this.matchPhase = settings;
    }
    public void setDiversity(DiversitySettings settings) {
        verifyThat(diversity == null, "already has diversity");
        this.diversity = settings;
    }

    public void setMinHitsPerThread(int minHits) {
        verifyThat(minHitsPerThread == null, "already has min-hits-per-thread");
        this.minHitsPerThread = minHits;
    }

    public void setNumSearchPartitions(int numParts) {
        verifyThat(numSearchPartitions == null, "already has num-search-partitions");
        this.numSearchPartitions = numParts;
    }

    public void setNumThreadsPerSearch(int threads) {
        verifyThat(numThreadsPerSearch == null, "already has num-threads-per-search");
        this.numThreadsPerSearch = threads;
    }

    public void setRankScoreDropLimit(double limit) {
        verifyThat(rankScoreDropLimit == null, "already has rank-score-drop-limit");
        this.rankScoreDropLimit = limit;
    }

    public void setSecondPhaseRankScoreDropLimit(double limit) {
        verifyThat(secondPhaseRankScoreDropLimit == null, "already has rank-score-drop-limit for second phase");
        this.secondPhaseRankScoreDropLimit = limit;
    }

    public void setGlobalPhaseRankScoreDropLimit(double limit) {
        verifyThat(globalPhaseRankScoreDropLimit == null, "already has global-phase rank-score-drop-limit");
        this.globalPhaseRankScoreDropLimit = limit;
    }

    public void setRerankCount(int count) {
        verifyThat(rerankCount == null, "already has rerank-count");
        this.rerankCount = count;
    }

    public void setTotalRerankCount(int count) {
        verifyThat(totalRerankCount == null, "already has total-rerank-count");
        this.totalRerankCount = count;
    }

    public void setSecondPhaseRanking(String expression) {
        verifyThat(secondPhaseExpression == null, "already has second-phase expression");
        this.secondPhaseExpression = expression;
    }

    public void setGlobalPhaseExpression(String expression) {
        verifyThat(globalPhaseExpression == null, "already has global-phase expression");
        this.globalPhaseExpression = expression;
    }

    public void setGlobalPhaseRerankCount(int count) {
        verifyThat(globalPhaseRerankCount == null, "already has global-phase rerank-count");
        this.globalPhaseRerankCount = count;
    }

    public void setStrict(boolean strict) {
        verifyThat(this.strict == null, "already has strict");
        this.strict = strict;
    }

    public void setUseSignificanceModel(boolean useSignificanceModel) {
        verifyThat(this.useSignificanceModel == null, "already has use-model");
        this.useSignificanceModel = useSignificanceModel;
    }

    public void setWeakandStopwordLimit(double limit) {
        verifyThat(this.weakandStopwordLimit == null, "already has weakand stopword-limit");
        verifyThat(limit >= 0.0 && limit <= 1.0, "weakand stopword-limit must be in range [0, 1]");
        this.weakandStopwordLimit = limit;
    }

    public void setWeakandAllowDropAll(boolean value) {
        verifyThat(this.weakandAllowDropAll == null, "already has weakand allow-drop-all");
        this.weakandAllowDropAll = value;
    }

    public void setWeakandAdjustTarget(double target) {
        verifyThat(this.weakandAdjustTarget == null, "already has weakand adjust-target");
        verifyThat(target >= 0.0 && target <= 1.0, "weakand adjust-target must be in range [0, 1]");
        this.weakandAdjustTarget = target;
    }

    public void setFilterThreshold(double threshold) {
        verifyThat(this.filterThreshold == null, "already has filter-threshold");
        verifyThat(threshold >= 0.0 && threshold <= 1.0, "filter-threshold must be in range [0, 1]");
        this.filterThreshold = threshold;
    }

    public void setTermwiseLimit(double limit) {
        verifyThat(termwiseLimit == null, "already has termwise-limit");
        this.termwiseLimit = limit;
    }

    public void setPostFilterThreshold(double threshold) {
        verifyThat(postFilterThreshold == null, "already has post-filter-threshold");
        this.postFilterThreshold = threshold;
    }

    public void setApproximateThreshold(double threshold) {
        verifyThat(approximateThreshold == null, "already has approximate-threshold");
        this.approximateThreshold = threshold;
    }

    public void setFilterFirstThreshold(double threshold) {
        verifyThat(filterFirstThreshold == null, "already has filter-first-threshold");
        this.filterFirstThreshold = threshold;
    }

    public void setFilterFirstExploration(double exploration) {
        verifyThat(filterFirstExploration == null, "already has filter-first-exploration");
        this.filterFirstExploration = exploration;
    }

    public void setExplorationSlack(double slack) {
        verifyThat(explorationSlack == null, "already has exploration-slack");
        this.explorationSlack = slack;
    }

    public void setPrefetchTensors(boolean value) {
        verifyThat(prefetchTensors == null, "already has prefetch-tensors");
        this.prefetchTensors = value;
    }

    public void setTargetHitsMaxAdjustmentFactor(double factor) {
        verifyThat(targetHitsMaxAdjustmentFactor == null, "already has target-hits-max-adjustment-factor");
        this.targetHitsMaxAdjustmentFactor = factor;
    }

}
