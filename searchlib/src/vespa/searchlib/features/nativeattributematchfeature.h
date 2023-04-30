// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "nativerankfeature.h"
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/searchlib/fef/itermfielddata.h>
#include <vespa/searchlib/fef/symmetrictable.h>

namespace search::features {

/**
 * This struct contains parameters used by the executor.
 **/
struct NativeAttributeMatchParam : public NativeParamBase
{
    NativeAttributeMatchParam() : NativeParamBase() { }
    fef::SymmetricTable weightBoostTable;
};
using NativeAttributeMatchParams = NativeRankParamsBase<NativeAttributeMatchParam>;

/**
 * Implements the executor for calculating the native attribute match score.
 **/
class NativeAttributeMatchExecutor : public fef::FeatureExecutor {
protected:
    struct CachedTermData {
        CachedTermData() : scale(0), weightBoostTable(NULL), tfh(fef::IllegalHandle) { }
        CachedTermData(const NativeAttributeMatchParams & params, const fef::ITermFieldData & tfd, feature_t s) :
             scale(s),
             weightBoostTable(&params.vector[tfd.getFieldId()].weightBoostTable),
             tfh(tfd.getHandle())
        { }
        feature_t scale;
        const fef::SymmetricTable * weightBoostTable;
        fef::TermFieldHandle tfh;
    };
    using CachedVector = std::vector<CachedTermData>;
    using Precomputed = std::pair<CachedVector, feature_t>;

    static feature_t calculateScore(const CachedTermData &td, const fef::TermFieldMatchData &tfmd);
private:
    static Precomputed preComputeSetup(const fef::IQueryEnvironment & env,
                                       const NativeAttributeMatchParams & params);

public:
    static fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment & env,
                                                const NativeAttributeMatchParams & params,
                                                vespalib::Stash &stash);
};

class NativeAttributeMatchExecutorMulti : public NativeAttributeMatchExecutor
{
private:
    feature_t                          _divisor;
    std::vector<CachedTermData>        _queryTermData;
    const fef::MatchData              *_md;

    void handle_bind_match_data(const fef::MatchData &md) override;
public:
    NativeAttributeMatchExecutorMulti(const Precomputed & setup) : _divisor(setup.second), _queryTermData(setup.first), _md(nullptr) { }
    void execute(uint32_t docId) override;
};

class NativeAttributeMatchExecutorSingle : public NativeAttributeMatchExecutor
{
private:
    CachedTermData _queryTermData;
    const fef::MatchData *_md;

    void handle_bind_match_data(const fef::MatchData &md) override;

public:
    NativeAttributeMatchExecutorSingle(const Precomputed & setup) :
        _queryTermData(setup.first[0]),
        _md(nullptr)
    {
        _queryTermData.scale /= setup.second;
    }
    void execute(uint32_t docId) override;
};


/**
 * Implements the blueprint for the native attribute match executor.
 **/
class NativeAttributeMatchBlueprint : public fef::Blueprint {
private:
    NativeAttributeMatchParams _params;

public:
    NativeAttributeMatchBlueprint();
    void visitDumpFeatures(const fef::IIndexEnvironment & env, fef::IDumpFeatureVisitor & visitor) const override;
    fef::Blueprint::UP createInstance() const override;

    fef::ParameterDescriptions getDescriptions() const override;
    bool setup(const fef::IIndexEnvironment & env, const fef::ParameterList & params) override;
    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;

    const NativeAttributeMatchParams & getParams() const { return _params; }
};

}
