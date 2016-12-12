// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/searchlib/fef/table.h>
#include "nativerankfeature.h"
#include "queryterm.h"
#include "termdistancecalculator.h"

namespace search {
namespace features {

/**
 * This struct contains parameters used by the executor.
 **/
struct NativeProximityParam : public NativeParamBase
{
    NativeProximityParam() : NativeParamBase(), proximityTable(NULL), revProximityTable(NULL), proximityImportance(0.5) { }
    const search::fef::Table * proximityTable;
    const search::fef::Table * revProximityTable;
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
class NativeProximityExecutor : public search::fef::FeatureExecutor {
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

    virtual void handle_bind_match_data(fef::MatchData &md) override;

public:
    NativeProximityExecutor(const search::fef::IQueryEnvironment & env,
                            const NativeProximityParams & params);
    virtual void execute(uint32_t docId);

    static void generateTermPairs(const search::fef::IQueryEnvironment & env, const QueryTermVector & terms,
                                  uint32_t slidingWindow, FieldSetup & setup);

    bool empty() const { return _setups.empty(); }
};


/**
 * Implements the blueprint for the native proximity executor.
 **/
class NativeProximityBlueprint : public search::fef::Blueprint {
private:
    NativeProximityParams _params;
    vespalib::string           _defaultProximityBoost;
    vespalib::string           _defaultRevProximityBoost;

public:
    NativeProximityBlueprint();

    // Inherit doc from Blueprint.
    virtual void visitDumpFeatures(const search::fef::IIndexEnvironment & env,
                                   search::fef::IDumpFeatureVisitor & visitor) const;

    // Inherit doc from Blueprint.
    virtual search::fef::Blueprint::UP createInstance() const;

    // Inherit doc from Blueprint.
    virtual search::fef::ParameterDescriptions getDescriptions() const {
        return search::fef::ParameterDescriptions().desc().field().repeat();
    }

    // Inherit doc from Blueprint.
    virtual bool setup(const search::fef::IIndexEnvironment & env,
                       const search::fef::ParameterList & params);

    // Inherit doc from Blueprint.
    virtual search::fef::FeatureExecutor &createExecutor(const search::fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;

    /**
     * Obtains the parameters used by the executor.
     **/
    const NativeProximityParams & getParams() const { return _params; }
};


} // namespace features
} // namespace search

