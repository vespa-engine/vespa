// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>

namespace search::features {

/**
 * Blueprint for the local document count used as BM25's fallback total document count.
 */
class NumDocsIndexedBlueprint : public fef::Blueprint {
public:
    NumDocsIndexedBlueprint();

    void visitDumpFeatures(const fef::IIndexEnvironment& env, fef::IDumpFeatureVisitor& visitor) const override;
    fef::Blueprint::UP createInstance() const override;
    fef::FeatureExecutor& createExecutor(const fef::IQueryEnvironment& env, vespalib::Stash& stash) const override;
    fef::ParameterDescriptions getDescriptions() const override { return fef::ParameterDescriptions().desc(); }
    bool setup(const fef::IIndexEnvironment& env, const fef::ParameterList& params) override;
};

} // namespace search::features
