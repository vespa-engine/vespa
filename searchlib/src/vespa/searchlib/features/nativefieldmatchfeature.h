// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/searchlib/fef/table.h>
#include "nativerankfeature.h"
#include "queryterm.h"

namespace search {
namespace features {

/**
 * This struct contains parameters used by the executor.
 **/
struct NativeFieldMatchParam : public NativeParamBase
{
    static const uint32_t NOT_DEF_FIELD_LENGTH;
    NativeFieldMatchParam() : NativeParamBase(), firstOccTable(NULL), numOccTable(NULL), averageFieldLength(NOT_DEF_FIELD_LENGTH), firstOccImportance(0.5) { }
    const search::fef::Table * firstOccTable;
    const search::fef::Table * numOccTable;
    uint32_t averageFieldLength;
    feature_t firstOccImportance;
};

class NativeFieldMatchParams : public NativeRankParamsBase<NativeFieldMatchParam>
{
public:
    uint32_t minFieldLength;
    NativeFieldMatchParams() : minFieldLength(6) { }
};

/**
 * Implements the executor for calculating the native field match score.
 **/
class NativeFieldMatchExecutor : public search::fef::FeatureExecutor
{
private:
    typedef std::vector<search::fef::TermFieldHandle> HandleVector;

    class MyQueryTerm : public QueryTerm
    {
    private:
        HandleVector _handles; // field match handles
    public:
        MyQueryTerm(const QueryTerm & qt) : QueryTerm(qt), _handles() {}
        HandleVector &handles() { return _handles; }
        const HandleVector &handles() const { return _handles; }
    };
    const NativeFieldMatchParams & _params;
    std::vector<MyQueryTerm>       _queryTerms;
    uint32_t                       _totalTermWeight;
    feature_t                      _divisor;

    VESPA_DLL_LOCAL feature_t calculateScore(const MyQueryTerm &qt, search::fef::MatchData &md);

    uint32_t getFieldLength(const NativeFieldMatchParam & param, uint32_t fieldLength) const {
        if (param.averageFieldLength != NativeFieldMatchParam::NOT_DEF_FIELD_LENGTH) {
            return param.averageFieldLength;
        }
        return fieldLength;
    }

    feature_t getFirstOccBoost(const NativeFieldMatchParam & param, uint32_t position, uint32_t fieldLength) const {
        const search::fef::Table * table = param.firstOccTable;
        size_t index = (position * (table->size() - 1)) / (std::max(_params.minFieldLength, fieldLength) - 1);
        return table->get(index);
    }

    feature_t getNumOccBoost(const NativeFieldMatchParam & param, uint32_t occs, uint32_t fieldLength) const {
        const search::fef::Table * table = param.numOccTable;
        size_t index = (occs * (table->size() - 1)) / (std::max(_params.minFieldLength, fieldLength));
        return table->get(index);
    }

public:
    NativeFieldMatchExecutor(const search::fef::IQueryEnvironment & env,
                             const NativeFieldMatchParams & params);
    virtual void execute(search::fef::MatchData & data);

    feature_t getFirstOccBoost(uint32_t field, uint32_t position, uint32_t fieldLength) const {
        return getFirstOccBoost(_params.vector[field], position, fieldLength);
    }

    feature_t getNumOccBoost(uint32_t field, uint32_t occs, uint32_t fieldLength) const {
        return getNumOccBoost(_params.vector[field], occs, fieldLength);
    }
    bool empty() const { return _queryTerms.empty(); }
};


/**
 * Implements the blueprint for the native field match executor.
 **/
class NativeFieldMatchBlueprint : public search::fef::Blueprint {
private:
    NativeFieldMatchParams _params;
    vespalib::string            _defaultFirstOcc;
    vespalib::string            _defaultNumOcc;

public:
    NativeFieldMatchBlueprint();

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
    virtual search::fef::FeatureExecutor::LP createExecutor(const search::fef::IQueryEnvironment & env) const override;

    /**
     * Obtains the parameters used by the executor.
     **/
    const NativeFieldMatchParams & getParams() const { return _params; }
};


} // namespace features
} // namespace search

