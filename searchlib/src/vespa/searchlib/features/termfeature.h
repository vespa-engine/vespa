// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/fef/blueprint.h>

namespace search::features {

/**
 * Implements the executor for term feature.
 */
class TermExecutor : public fef::FeatureExecutor {
public:
    /**
     * Constructs an executor for term feature.
     *
     * @param env    The query environment.
     * @param termId The id of the query term to evaluate.
     */
    TermExecutor(const fef::IQueryEnvironment &env, uint32_t termId);
    void execute(uint32_t docId) override;

private:
    const fef::ITermData *_termData;
    feature_t             _connectedness;
    feature_t             _significance;
};

/**
 * Implements the blueprint for term feature.
 */
class TermBlueprint : public fef::Blueprint {
public:
    TermBlueprint();

    void visitDumpFeatures(const fef::IIndexEnvironment &env, fef::IDumpFeatureVisitor &visitor) const override;
    fef::Blueprint::UP createInstance() const override;
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().desc().number();
    }
    bool setup(const fef::IIndexEnvironment & env, const fef::ParameterList & params) override;
    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;

private:
    uint32_t _termId;
};

}
