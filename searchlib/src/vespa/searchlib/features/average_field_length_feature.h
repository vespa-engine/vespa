// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>

namespace search::features {

/**
 * Blueprint for the index-maintained average field length statistic used for BM25 normalization.
 * The value comes from the local content node index (memory or disk), not from bm25 rank-properties.
 */
class AverageFieldLengthBlueprint : public fef::Blueprint {
    const fef::FieldInfo* _field;

public:
    AverageFieldLengthBlueprint();

    void visitDumpFeatures(const fef::IIndexEnvironment& env, fef::IDumpFeatureVisitor& visitor) const override;
    fef::Blueprint::UP createInstance() const override;
    fef::FeatureExecutor& createExecutor(const fef::IQueryEnvironment& env, vespalib::Stash& stash) const override;
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().desc().indexField(fef::ParameterCollection::ANY);
    }
    bool setup(const fef::IIndexEnvironment& env, const fef::ParameterList& params) override;
};

} // namespace search::features
