// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "nativerankfeature.h"
#include "termdistancecalculator.h"
#include <map>

namespace search::features {

/**
 * This struct contains parameters used by the executor.
 **/
struct NativeProximityParam : public NativeParamBase
{
    NativeProximityParam() noexcept : NativeParamBase(), proximityTable(NULL), revProximityTable(NULL), proximityImportance(0.5) { }
    const fef::Table * proximityTable;
    const fef::Table * revProximityTable;
    feature_t proximityImportance;
};

class NativeProximityParams : public NativeRankParamsBase<NativeProximityParam>
{
public:
    uint32_t slidingWindow;
    NativeProximityParams() : slidingWindow(4) { }
};

/**
 * Class containing shared state for native proximity executor.
 */
class NativeProximityExecutorSharedState : public fef::Anything {
public:
public:
    /**
     * Represents a term pair with connectedness and associated term distance calculator.
     **/
    struct TermPair {
        QueryTerm first;
        QueryTerm second;
        feature_t connectedness;
        TermPair(QueryTerm f, QueryTerm s, feature_t c) :
            first(f), second(s), connectedness(c) {}
    };
    typedef std::vector<TermPair> TermPairVector;
    /**
     * Represents the setup needed to calculate the proximity score for a single field.
     **/
    struct FieldSetup {
        uint32_t fieldId;
        TermPairVector pairs;
        feature_t divisor;
        FieldSetup(uint32_t fid) : fieldId(fid), pairs(), divisor(0) {}
    };
private:
    const NativeProximityParams&  _params;
    std::vector<FieldSetup>       _setups;
    uint32_t                      _total_field_weight;
    std::map<uint32_t, QueryTermVector> _fields;

public:
    NativeProximityExecutorSharedState(const fef::IQueryEnvironment& env, const NativeProximityParams& params);
    ~NativeProximityExecutorSharedState();
    static void generateTermPairs(const fef::IQueryEnvironment& env, const QueryTermVector& terms,
                                  uint32_t slidingWindow, FieldSetup& setup);
    const std::vector<FieldSetup>& get_setups() const { return _setups; }
    const NativeProximityParams& get_params() const { return _params; }
    uint32_t get_total_field_weight() const { return _total_field_weight; }
    bool empty() const { return _setups.empty(); }
    const std::map<uint32_t, QueryTermVector>& get_fields() const { return _fields; }
};

/**
 * Implements the executor for calculating the native proximity score.
 **/
class NativeProximityExecutor : public fef::FeatureExecutor {
public:
    using TermPair = NativeProximityExecutorSharedState::TermPair;
    using FieldSetup = NativeProximityExecutorSharedState::FieldSetup;
private:
    const NativeProximityParams & _params;
    vespalib::ConstArrayRef<FieldSetup> _setups;
    uint32_t                      _totalFieldWeight;
    const fef::MatchData         *_md;

    feature_t calculateScoreForField(const FieldSetup & fs, uint32_t docId);
    feature_t calculateScoreForPair(const TermPair & pair, uint32_t fieldId, uint32_t docId);

    virtual void handle_bind_match_data(const fef::MatchData &md) override;

public:
    NativeProximityExecutor(const NativeProximityExecutorSharedState& shared_state);
    void execute(uint32_t docId) override;
};


/**
 * Implements the blueprint for the native proximity executor.
 **/
class NativeProximityBlueprint : public fef::Blueprint {
private:
    NativeProximityParams _params;
    vespalib::string      _defaultProximityBoost;
    vespalib::string      _defaultRevProximityBoost;
    vespalib::string      _shared_state_key;

public:
    NativeProximityBlueprint();
    ~NativeProximityBlueprint();
    void visitDumpFeatures(const fef::IIndexEnvironment & env, fef::IDumpFeatureVisitor & visitor) const override;
    fef::Blueprint::UP createInstance() const override;
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().desc().field().repeat();
    }
    bool setup(const fef::IIndexEnvironment & env, const fef::ParameterList & params) override;
    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
    
    const NativeProximityParams & getParams() const { return _params; }

    void prepareSharedState(const fef::IQueryEnvironment &queryEnv, fef::IObjectStore &objectStore) const override;
};

}
