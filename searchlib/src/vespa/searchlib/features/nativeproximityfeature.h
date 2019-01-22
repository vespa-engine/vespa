// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "nativerankfeature.h"
#include "queryterm.h"
#include "termdistancecalculator.h"

namespace search::features {

/**
 * This struct contains parameters used by the executor.
 **/
struct NativeProximityParam : public NativeParamBase
{
    NativeProximityParam() : NativeParamBase(), proximityTable(NULL), revProximityTable(NULL), proximityImportance(0.5) { }
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
 * Implements the executor for calculating the native proximity score.
 **/
class NativeProximityExecutor : public fef::FeatureExecutor {
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
    const NativeProximityParams & _params;
    std::vector<FieldSetup>       _setups;
    uint32_t                      _totalFieldWeight;
    const fef::MatchData         *_md;

    feature_t calculateScoreForField(const FieldSetup & fs, uint32_t docId);
    feature_t calculateScoreForPair(const TermPair & pair, uint32_t fieldId, uint32_t docId);

    virtual void handle_bind_match_data(const fef::MatchData &md) override;

public:
    NativeProximityExecutor(const fef::IQueryEnvironment & env, const NativeProximityParams & params);
    void execute(uint32_t docId) override;

    static void generateTermPairs(const fef::IQueryEnvironment & env, const QueryTermVector & terms,
                                  uint32_t slidingWindow, FieldSetup & setup);

    bool empty() const { return _setups.empty(); }
};


/**
 * Implements the blueprint for the native proximity executor.
 **/
class NativeProximityBlueprint : public fef::Blueprint {
private:
    NativeProximityParams _params;
    vespalib::string           _defaultProximityBoost;
    vespalib::string           _defaultRevProximityBoost;

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
};

}
