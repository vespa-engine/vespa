// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "nativerankfeature.h"
#include "queryterm.h"

namespace search::features {

/**
 * This struct contains parameters used by the executor.
 **/
struct NativeFieldMatchParam : public NativeParamBase
{
    static const uint32_t NOT_DEF_FIELD_LENGTH;
    NativeFieldMatchParam() noexcept : NativeParamBase(), firstOccTable(NULL), numOccTable(NULL), averageFieldLength(NOT_DEF_FIELD_LENGTH), firstOccImportance(0.5) { }
    const fef::Table * firstOccTable;
    const fef::Table * numOccTable;
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
 * Class containing shared state for native field match executor.
 */
class NativeFieldMatchExecutorSharedState : public fef::Anything {
public:
    using WrappedHandle = std::pair<fef::TermFieldHandle, const fef::ITermFieldData*>;
    using HandleVector = std::vector<WrappedHandle>;
    class MyQueryTerm : public QueryTerm
    {
    private:
        HandleVector _handles; // field match handles
    public:
        MyQueryTerm(const QueryTerm & qt) : QueryTerm(qt), _handles() {}
        HandleVector &handles() { return _handles; }
        const HandleVector &handles() const { return _handles; }
    };
private:
    const NativeFieldMatchParams& _params;
    std::vector<MyQueryTerm>      _query_terms;
    feature_t                     _divisor;
public:
    NativeFieldMatchExecutorSharedState(const fef::IQueryEnvironment& env, const NativeFieldMatchParams& params);
    ~NativeFieldMatchExecutorSharedState();
    const NativeFieldMatchParams& get_params() const { return _params; }
    const std::vector<MyQueryTerm>& get_query_terms() const { return _query_terms; }
    feature_t get_divisor() const { return _divisor; }
    bool empty() const { return _query_terms.empty(); }
};

/**
 * Implements the executor for calculating the native field match score.
 **/
class NativeFieldMatchExecutor : public fef::FeatureExecutor
{
private:
    using MyQueryTerm = NativeFieldMatchExecutorSharedState::MyQueryTerm;
    const NativeFieldMatchParams & _params;
    vespalib::ConstArrayRef<MyQueryTerm> _queryTerms;
    feature_t                      _divisor;
    const fef::MatchData          *_md;

    VESPA_DLL_LOCAL feature_t calculateScore(const MyQueryTerm &qt, uint32_t docId);

    uint32_t getFieldLength(const NativeFieldMatchParam & param, uint32_t fieldLength) const {
        if (param.averageFieldLength != NativeFieldMatchParam::NOT_DEF_FIELD_LENGTH) {
            return param.averageFieldLength;
        }
        return fieldLength;
    }

    feature_t getFirstOccBoost(const NativeFieldMatchParam & param, uint32_t position, uint32_t fieldLength) const {
        const fef::Table * table = param.firstOccTable;
        size_t index = (position * (table->size() - 1)) / (std::max(_params.minFieldLength, fieldLength) - 1);
        return table->get(index);
    }

    feature_t getNumOccBoost(const NativeFieldMatchParam & param, uint32_t occs, uint32_t fieldLength) const {
        const fef::Table * table = param.numOccTable;
        size_t index = (occs * (table->size() - 1)) / (std::max(_params.minFieldLength, fieldLength));
        return table->get(index);
    }

    virtual void handle_bind_match_data(const fef::MatchData &md) override;

public:
    NativeFieldMatchExecutor(const NativeFieldMatchExecutorSharedState& shared_state);
    void execute(uint32_t docId) override;

    feature_t getFirstOccBoost(uint32_t field, uint32_t position, uint32_t fieldLength) const {
        return getFirstOccBoost(_params.vector[field], position, fieldLength);
    }

    feature_t getNumOccBoost(uint32_t field, uint32_t occs, uint32_t fieldLength) const {
        return getNumOccBoost(_params.vector[field], occs, fieldLength);
    }
};


/**
 * Implements the blueprint for the native field match executor.
 **/
class NativeFieldMatchBlueprint : public fef::Blueprint {
private:
    NativeFieldMatchParams _params;
    vespalib::string            _defaultFirstOcc;
    vespalib::string            _defaultNumOcc;
    vespalib::string            _shared_state_key;

public:
    NativeFieldMatchBlueprint();
    ~NativeFieldMatchBlueprint();
    void visitDumpFeatures(const fef::IIndexEnvironment & env, fef::IDumpFeatureVisitor & visitor) const override;
    fef::Blueprint::UP createInstance() const override;
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().desc().field().repeat();
    }
    bool setup(const fef::IIndexEnvironment & env, const fef::ParameterList & params) override;
    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment & env, vespalib::Stash &stash) const override;
    
    const NativeFieldMatchParams & getParams() const { return _params; }

    void prepareSharedState(const fef::IQueryEnvironment &queryEnv, fef::IObjectStore &objectStore) const override;
};

}
