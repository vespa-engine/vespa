// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/fieldtype.h>
#include <vespa/searchlib/fef/table.h>
#include <cassert>

namespace search::features {

/**
 * This struct contains parameters used by the nativeRank executor.
 **/
struct NativeRankParams {
    feature_t fieldMatchWeight;
    feature_t attributeMatchWeight;
    feature_t proximityWeight;
    NativeRankParams() : fieldMatchWeight(0), attributeMatchWeight(0), proximityWeight(0) {}
};

/**
 * The base class for parameter classes used by native rank sub executors.
 **/
struct NativeParamBase {
    NativeParamBase() : maxTableSum(1), fieldWeight(100), field(false) { }
    double   maxTableSum;
    uint32_t fieldWeight;
    bool     field;
};
template <class P>
class NativeRankParamsBase {
public:
    using Param = P;
    std::vector<P> vector;
    NativeRankParamsBase() : vector() {}
    void resize(size_t numFields) {
        vector.resize(numFields);
    }
    void setMaxTableSums(size_t fieldId, double value) {
        vector[fieldId].maxTableSum = value;
        if (vector[fieldId].maxTableSum == 0) {
            vector[fieldId].maxTableSum = 1;
        }
    }
    bool considerField(size_t fieldId) const {
        assert(fieldId < vector.size());
        return vector[fieldId].field;
    }
};

/**
 * This class wraps an index environment and serves fields of a certain type.
 * You can specify a set of field names to consider instead of all found in the index environment.
 **/
class FieldWrapper {
public:
    std::vector<const fef::FieldInfo *> _fields;

public:
    /**
     * Creates a new wrapper.
     *
     * @param env the environment to wrap.
     * @param fieldNames the set of field names to consider. If empty all found in the environment are used.
     * @param filter the field type this wrapper should let through.
     **/
    FieldWrapper(const fef::IIndexEnvironment & env,
                 const fef::ParameterList & fields,
                 const fef::FieldType filter);
    size_t getNumFields() const { return _fields.size(); }
    const fef::FieldInfo * getField(size_t idx) const { return _fields[idx]; }
};

/**
 * Implements the executor for calculating the native rank score.
 **/
class NativeRankExecutor : public fef::FeatureExecutor {
private:
    const NativeRankParams & _params;
    feature_t                _divisor;

public:
    NativeRankExecutor(const NativeRankParams & params);
    void execute(uint32_t docId) override;
};


/**
 * Implements the blueprint for the native rank executor.
 **/
class NativeRankBlueprint : public fef::Blueprint {
private:
    NativeRankParams _params;

public:
    NativeRankBlueprint();

    void visitDumpFeatures(const fef::IIndexEnvironment & env, fef::IDumpFeatureVisitor & visitor) const override;
    fef::Blueprint::UP createInstance() const override;
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().desc().field().repeat();
    }
    bool setup(const fef::IIndexEnvironment & env, const fef::ParameterList & params) override;
    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;

    /**
     * Obtains the parameters used by the executor.
     **/
    const NativeRankParams & getParams() const { return _params; }

    /**
     * Returns whether we should use table normalization for the setup using the given environment.
     **/
    static bool useTableNormalization(const fef::IIndexEnvironment & env);
};

}
