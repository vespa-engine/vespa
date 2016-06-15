// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/searchlib/fef/table.h>
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/searchlib/fef/itermfielddata.h>
#include "nativerankfeature.h"
#include <vespa/searchlib/fef/symmetrictable.h>

namespace search {
namespace features {

/**
 * This struct contains parameters used by the executor.
 **/
struct NativeAttributeMatchParam : public NativeParamBase
{
    NativeAttributeMatchParam() : NativeParamBase() { }
    fef::SymmetricTable weightBoostTable;
};
typedef NativeRankParamsBase<NativeAttributeMatchParam> NativeAttributeMatchParams;

/**
 * Implements the executor for calculating the native attribute match score.
 **/
class NativeAttributeMatchExecutor : public fef::FeatureExecutor {
protected:
    struct CachedTermData {
        CachedTermData() : scale(0), weightBoostTable(NULL), tfh(search::fef::IllegalHandle) { }
        CachedTermData(const NativeAttributeMatchParams & params, const fef::ITermFieldData & tfd, feature_t s) :
             scale(s),
             weightBoostTable(&params.vector[tfd.getFieldId()].weightBoostTable),
             tfh(tfd.getHandle())
        { }
        feature_t scale;
        const fef::SymmetricTable * weightBoostTable;
        fef::TermFieldHandle tfh;
    };
    typedef std::vector<CachedTermData> CachedVector;
    typedef std::pair<CachedVector, feature_t> Precomputed;

    static feature_t calculateScore(const CachedTermData &td, const fef::TermFieldMatchData &tfmd);
private:
    static Precomputed preComputeSetup(const fef::IQueryEnvironment & env,
                                        const NativeAttributeMatchParams & params);

public:
    static fef::FeatureExecutor::LP createExecutor(const fef::IQueryEnvironment & env,
                                                           const NativeAttributeMatchParams & params);
};

class NativeAttributeMatchExecutorMulti : public NativeAttributeMatchExecutor
{
private:
    feature_t                          _divisor;
    std::vector<CachedTermData>        _queryTermData;
public:
    NativeAttributeMatchExecutorMulti(const Precomputed & setup) : _divisor(setup.second), _queryTermData(setup.first) { }
    // Inherit doc from FeatureExecutor.
    virtual void execute(fef::MatchData & data);
};

class NativeAttributeMatchExecutorSingle : public NativeAttributeMatchExecutor
{
private:
    CachedTermData _queryTermData;
public:
    NativeAttributeMatchExecutorSingle(const Precomputed & setup) :
        _queryTermData(setup.first[0])
    {
        _queryTermData.scale /= setup.second;
    }
    // Inherit doc from FeatureExecutor.
    virtual void execute(fef::MatchData & data);
};


/**
 * Implements the blueprint for the native attribute match executor.
 **/
class NativeAttributeMatchBlueprint : public fef::Blueprint {
private:
    NativeAttributeMatchParams _params;

public:
    NativeAttributeMatchBlueprint();

    // Inherit doc from Blueprint.
    virtual void visitDumpFeatures(const fef::IIndexEnvironment & env,
                                   fef::IDumpFeatureVisitor & visitor) const;

    // Inherit doc from Blueprint.
    virtual fef::Blueprint::UP createInstance() const;

    // Inherit doc from Blueprint.
    virtual fef::ParameterDescriptions getDescriptions() const {
        return fef::ParameterDescriptions().desc().attribute(search::fef::ParameterCollection::ANY).repeat();
    }

    // Inherit doc from Blueprint.
    virtual bool setup(const fef::IIndexEnvironment & env,
                       const fef::ParameterList & params);

    // Inherit doc from Blueprint.
    virtual fef::FeatureExecutor::LP createExecutor(const fef::IQueryEnvironment & env) const;

    /**
     * Obtains the parameters used by the executor.
     **/
    const NativeAttributeMatchParams & getParams() const { return _params; }
};


} // namespace features
} // namespace search

