// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "blueprintfactory.h"
#include "iindexenvironment.h"
#include "iqueryenvironment.h"
#include "blueprintresolver.h"
#include "rank_program.h"
#include <vespa/searchlib/common/stringmap.h>

namespace search::fef {

/**
 * A rank setup contains information about how initial and final rank
 * should be calculated. A rank setup is responsible for resolving
 * dependencies between named features and also acts as a factory for
 * @ref RankContext objects. In addition to keeping track of how to
 * calculate rank, a RankSetup also keeps track of how to calculate
 * all features that should be dumped when performing a full feature
 * dump.
 **/
class RankSetup
{
public:
    using Warnings = BlueprintResolver::Warnings;
    struct MutateOperation {
    public:
        MutateOperation() : MutateOperation("", "") {}
        MutateOperation(vespalib::stringref attribute, vespalib::stringref operation)
            : _attribute(attribute),
              _operation(operation)
        {}
        bool enabled() const { return !_attribute.empty() && !_operation.empty(); }
        vespalib::string _attribute;
        vespalib::string _operation;
    };
private:
    const BlueprintFactory  &_factory;
    const IIndexEnvironment &_indexEnv;
    BlueprintResolver::SP    _first_phase_resolver;
    BlueprintResolver::SP    _second_phase_resolver;
    BlueprintResolver::SP    _match_resolver;
    BlueprintResolver::SP    _summary_resolver;
    BlueprintResolver::SP    _dumpResolver;
    vespalib::string         _firstPhaseRankFeature;
    vespalib::string         _secondPhaseRankFeature;
    vespalib::string         _degradationAttribute;
    double                   _termwise_limit;
    uint32_t                 _numThreads;
    uint32_t                 _minHitsPerThread;
    uint32_t                 _numSearchPartitions;
    uint32_t                 _heapSize;
    uint32_t                 _arraySize;
    uint32_t                 _estimatePoint;
    uint32_t                 _estimateLimit;
    uint32_t                 _degradationMaxHits;
    double                   _degradationMaxFilterCoverage;
    double                   _degradationSamplePercentage;
    double                   _degradationPostFilterMultiplier;
    feature_t                _rankScoreDropLimit;
    std::vector<vespalib::string> _match_features;
    std::vector<vespalib::string> _summaryFeatures;
    std::vector<vespalib::string> _dumpFeatures;
    Warnings                 _warnings;
    StringStringMap          _feature_rename_map;
    bool                     _ignoreDefaultRankFeatures;
    bool                     _compiled;
    bool                     _compileError;
    bool                     _degradationAscendingOrder;
    vespalib::string         _diversityAttribute;
    uint32_t                 _diversityMinGroups;
    double                   _diversityCutoffFactor;
    vespalib::string         _diversityCutoffStrategy;
    bool                     _softTimeoutEnabled;
    double                   _softTimeoutTailCost;
    double                   _softTimeoutFactor;
    double                   _global_filter_lower_limit;
    double                   _global_filter_upper_limit;
    MutateOperation          _mutateOnMatch;
    MutateOperation          _mutateOnFirstPhase;
    MutateOperation          _mutateOnSecondPhase;
    MutateOperation          _mutateOnSummary;
    bool                     _mutateAllowQueryOverride;

    void compileAndCheckForErrors(BlueprintResolver &bp);
public:
    RankSetup(const RankSetup &) = delete;
    RankSetup &operator=(const RankSetup &) = delete;
    /**
     * Convenience typedef for a shared pointer to this class.
     **/
    typedef std::shared_ptr<RankSetup> SP;

    /**
     * Create a new rank setup within the given index environment and
     * backed by the given factory.
     *
     * @param factory blueprint factory
     * @param indexEnv index environment
     **/
    RankSetup(const BlueprintFactory &factory, const IIndexEnvironment &indexEnv);

    ~RankSetup();

    /**
     * Configures this rank setup according to the fef properties
     * found in the index environment.
     **/
    void configure();

    /**
     * This method is invoked during setup (before invoking the @ref
     * compile method) to define what feature to use as first phase
     * ranking.
     *
     * @param featureName full feature name for first phase rank
     **/
    void setFirstPhaseRank(const vespalib::string &featureName);

    /**
     * Returns the first phase ranking.
     *
     * @return feature name for first phase rank
     **/
    const vespalib::string &getFirstPhaseRank() const { return _firstPhaseRankFeature; }

    /**
     * This method is invoked during setup (before invoking the @ref
     * compile method) to define what feature to use as second phase ranking.
     *
     * @param featureName full feature name for second phase rank
     **/
    void setSecondPhaseRank(const vespalib::string &featureName);

    /**
     * Returns the second phase ranking.
     *
     * @return feature name for second phase rank
     **/
    const vespalib::string &getSecondPhaseRank() const { return _secondPhaseRankFeature; }

    /**
     * Set the termwise limit
     *
     * The termwise limit is a number in the range [0,1] indicating
     * how much of the corpus the query must match for termwise
     * evaluation to be enabled.
     *
     * @param value termwise limit
     **/
    void set_termwise_limit(double value) { _termwise_limit = value; }

    /**
     * Get the termwise limit
     *
     * The termwise limit is a number in the range [0,1] indicating
     * how much of the corpus the query must match for termwise
     * evaluation to be enabled.
     *
     * @return termwise limit
     **/
    double get_termwise_limit() const { return _termwise_limit; }

    /**
     * Sets the number of threads per search.
     *
     * @param numThreads the number of threads
     **/
    void setNumThreadsPerSearch(uint32_t numThreads) { _numThreads = numThreads; }

    /**
     * Returns the number of threads per search.
     *
     * @return the number of threads
     **/
    uint32_t getNumThreadsPerSearch() const { return _numThreads; }
    uint32_t getMinHitsPerThread() const { return _minHitsPerThread; }
    void setMinHitsPerThread(uint32_t minHitsPerThread) { _minHitsPerThread = minHitsPerThread; }

    void setNumSearchPartitions(uint32_t numSearchPartitions) { _numSearchPartitions = numSearchPartitions; }

    uint32_t getNumSearchPartitions() const { return _numSearchPartitions; }

    /**
     * Sets the heap size to be used in the hit collector.
     *
     * @param heapSize the heap size
     **/
    void setHeapSize(uint32_t heapSize) { _heapSize = heapSize; }

    /**
     * Returns the heap size to be used in the hit collector.
     *
     * @return the heap size
     **/
    uint32_t getHeapSize() const { return _heapSize; }

    /**
     * Sets the array size to be used in the hit collector.
     *
     * @param arraySize the array size
     **/
    void setArraySize(uint32_t arraySize) { _arraySize = arraySize; }

    /**
     * Returns the array size to be used in the hit collector.
     *
     * @return the array size
     **/
    uint32_t getArraySize() const { return _arraySize; }

    /** whether match phase should do graceful degradation */
    bool hasMatchPhaseDegradation() const {
        return (_degradationAttribute.size() > 0);
    }

    /** get name of attribute to use for graceful degradation in match phase */
    vespalib::string getDegradationAttribute() const {
        return _degradationAttribute;
    }
    /** check whether attribute should be used in ascending order during graceful degradation in match phase */
    bool isDegradationOrderAscending() const {
        return _degradationAscendingOrder;
    }
    /** get number of hits to collect during graceful degradation in match phase */
    uint32_t getDegradationMaxHits() const {
        return _degradationMaxHits;
    }

    double getDegradationMaxFilterCoverage() const { return _degradationMaxFilterCoverage; }
    /** get number of hits to collect during graceful degradation in match phase */
    double getDegradationSamplePercentage() const {
        return _degradationSamplePercentage;
    }

    /** get number of hits to collect during graceful degradation in match phase */
    double getDegradationPostFilterMultiplier() const {
        return _degradationPostFilterMultiplier;
    }

    /** get the attribute used to ensure diversity during match phase limiting **/
    vespalib::string getDiversityAttribute() const {
        return _diversityAttribute;
    }

    /** get the minimal diversity we should try to achieve **/
    uint32_t getDiversityMinGroups() const {
        return _diversityMinGroups;
    }

    double getDiversityCutoffFactor() const {
        return _diversityCutoffFactor;
    }

    const vespalib::string & getDiversityCutoffStrategy() const {
        return _diversityCutoffStrategy;
    }

    /** set name of attribute to use for graceful degradation in match phase */
    void setDegradationAttribute(const vespalib::string &name) {
        _degradationAttribute = name;
    }
    /** set whether attribute should be used in ascending order during graceful degradation in match phase */
    void setDegradationOrderAscending(bool ascending) {
        _degradationAscendingOrder = ascending;
    }
    /** set number of hits to collect during graceful degradation in match phase */
    void setDegradationMaxHits(uint32_t maxHits) {
        _degradationMaxHits = maxHits;
    }

    void setDegradationMaxFilterCoverage(double degradationMaxFilterCoverage) {
        _degradationMaxFilterCoverage = degradationMaxFilterCoverage;
    }

    /** set number of hits to collect during graceful degradation in match phase */
    void setDegradationSamplePercentage(double samplePercentage) {
        _degradationSamplePercentage = samplePercentage;
    }

    /** set number of hits to collect during graceful degradation in match phase */
    void setDegradationPostFilterMultiplier(double samplePercentage) {
        _degradationPostFilterMultiplier = samplePercentage;
    }

    /** set the attribute used to ensure diversity during match phase limiting **/
    void setDiversityAttribute(const vespalib::string &value) {
        _diversityAttribute = value;
    }

    /** set the minimal diversity we should try to achieve **/
    void setDiversityMinGroups(uint32_t value) {
        _diversityMinGroups = value;
    }

    void setDiversityCutoffFactor(double value) {
        _diversityCutoffFactor = value;
    }

    void setDiversityCutoffStrategy(const vespalib::string & value) {
        _diversityCutoffStrategy  = value;
    }

    /**
     * Sets the estimate point to be used in parallel query evaluation.
     *
     * @param estimatePoint the estimate point
     **/
    void setEstimatePoint(uint32_t estimatePoint) { _estimatePoint = estimatePoint; }

    /**
     * Returns the estimate point to be used in parallel query evaluation.
     *
     * @return the estimate point
     **/
    uint32_t getEstimatePoint() const { return _estimatePoint; }

    /**
     * Sets the estimate limit to be used in parallel query evaluation.
     *
     * @param estimateLimit the estimate limit
     **/
    void setEstimateLimit(uint32_t estimateLimit) { _estimateLimit = estimateLimit; }

    /**
     * Returns the estimate limit to be used in parallel query evaluation.
     *
     * @return the estimate limit
     **/
    uint32_t getEstimateLimit() const { return _estimateLimit; }

    /**
     * Sets the rank score drop limit to be used in parallel query evaluation.
     *
     * @param rankScoreDropLimit the rank score drop limit
     **/
    void setRankScoreDropLimit(feature_t rankScoreDropLimit) { _rankScoreDropLimit = rankScoreDropLimit; }

    /**
     * Returns the rank score drop limit to be used in parallel query evaluation.
     *
     * @return the rank score drop limit
     **/
    feature_t getRankScoreDropLimit() const { return _rankScoreDropLimit; }

    /**
     * This method may be used to indicate that certain features
     * should be present in the search result.
     *
     * @param match_feature full feature name of a match feature
     **/
    void add_match_feature(const vespalib::string &match_feature);

    /**
     * This method may be used to indicate that certain features
     * should be present in the docsum.
     *
     * @param summaryFeature full feature name of a summary feature
     **/
    void addSummaryFeature(const vespalib::string &summaryFeature);

    /**
     * @return whether there are any match features
     **/
    bool has_match_features() const { return !_match_features.empty(); }

    /**
     * Returns a const view of the match features added.
     *
     * @return vector of match feature names.
     **/
    const std::vector<vespalib::string> &get_match_features() const { return _match_features; }

    const StringStringMap &get_feature_rename_map() const { return _feature_rename_map; }

    /**
     * Returns a const view of the summary features added.
     *
     * @return vector of summary feature names.
     **/
    const std::vector<vespalib::string> &getSummaryFeatures() const { return _summaryFeatures; }

    /**
     * Set the flag indicating whether we should ignore the default
     * rank features (the ones specified by the plugins themselves)
     *
     * @param flag true means ignore default rank features
     **/
    void setIgnoreDefaultRankFeatures(bool flag) { _ignoreDefaultRankFeatures = flag; }

    /**
     * Get the flag indicating whether we should ignore the default
     * rank features (the ones specified by the plugins themselves)
     *
     * @return true means ignore default rank features
     **/
    bool getIgnoreDefaultRankFeatures() { return _ignoreDefaultRankFeatures; }

    void setSoftTimeoutEnabled(bool v) { _softTimeoutEnabled = v; }
    bool getSoftTimeoutEnabled() const { return _softTimeoutEnabled; }
    void setSoftTimeoutTailCost(double v) { _softTimeoutTailCost = v; }
    double getSoftTimeoutTailCost() const { return _softTimeoutTailCost; }
    void setSoftTimeoutFactor(double v) { _softTimeoutFactor = v; }
    double getSoftTimeoutFactor() const { return _softTimeoutFactor; }

    void set_global_filter_lower_limit(double v) { _global_filter_lower_limit = v; }
    double get_global_filter_lower_limit() const { return _global_filter_lower_limit; }
    void set_global_filter_upper_limit(double v) { _global_filter_upper_limit = v; }
    double get_global_filter_upper_limit() const { return _global_filter_upper_limit; }

    /**
     * This method may be used to indicate that certain features
     * should be dumped during a full feature dump.
     *
     * @param dumpFeature full feature name of a dump feature
     **/
    void addDumpFeature(const vespalib::string &dumpFeature);

    /**
     * Returns a const view of the dump features added.
     *
     * @return vector of dump feature names.
     **/
    const std::vector<vespalib::string> &getDumpFeatures() const { return _dumpFeatures; }

    /**
     * Create blueprints, resolve dependencies and form a strategy for
     * how to create feature executors used to calculate initial and
     * final rank for individual queries. This method must be invoked
     * after the @ref setInitialRank and @ref setFinalRank methods and
     * before creating @ref RankContext objects using the @ref
     * createRankContext and @ref createDumpContext methods.
     *
     * @return true if things went ok, false otherwise (dependency issues)
     **/
    bool compile();

    /**
     * Will return any accumulated warnings during compile
     * @return joined string of warnings separated by newline
     */
    vespalib::string getJoinedWarnings() const;

    // These functions create rank programs for different tasks. Note
    // that the setup function must be called on rank programs for
    // them to be ready to use. Also keep in mind that creating a rank
    // program is cheap while setting it up is more expensive.

    RankProgram::UP create_first_phase_program() const { return std::make_unique<RankProgram>(_first_phase_resolver); }
    RankProgram::UP create_second_phase_program() const { return std::make_unique<RankProgram>(_second_phase_resolver); }
    RankProgram::UP create_match_program() const { return std::make_unique<RankProgram>(_match_resolver); }
    RankProgram::UP create_summary_program() const { return std::make_unique<RankProgram>(_summary_resolver); }
    RankProgram::UP create_dump_program() const { return std::make_unique<RankProgram>(_dumpResolver); }

    /**
     * Here you can do some preprocessing. State must be stored in the IObjectStore.
     * This is called before creating multiple execution threads.
     * @param queryEnv The query environment.
     */
    void prepareSharedState(const IQueryEnvironment & queryEnv, IObjectStore & objectStore) const;

    const MutateOperation & getMutateOnMatch() const { return _mutateOnMatch; }
    const MutateOperation & getMutateOnFirstPhase() const { return _mutateOnFirstPhase; }
    const MutateOperation & getMutateOnSecondPhase() const { return _mutateOnSecondPhase; }
    const MutateOperation & getMutateOnSummary() const { return _mutateOnSummary; }

    bool allowMutateQueryOverride() const { return _mutateAllowQueryOverride; }
};

}
