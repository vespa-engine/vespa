// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/fef/blueprint.h>

namespace search::features {

/**
 * Implements the executor for term feature.
 */
class FieldTermMatchExecutor : public fef::FeatureExecutor {
public:
    /**
     * Constructs an executor for term feature.
     *
     * @param env     The query environment.
     * @param fieldId The field to match to.
     * @param termId  The term to match.
     */
    FieldTermMatchExecutor(const fef::IQueryEnvironment &env,
                           uint32_t fieldId, uint32_t termId);
    void execute(uint32_t docId) override;

private:
    fef::TermFieldHandle _fieldHandle;
    const fef::MatchData        *_md;

    void handle_bind_match_data(const fef::MatchData &md) override;
};

/**
 * Implements the blueprint for term feature.
 */
class FieldTermMatchBlueprint : public fef::Blueprint {
public:
    FieldTermMatchBlueprint();
    void visitDumpFeatures(const fef::IIndexEnvironment &env, fef::IDumpFeatureVisitor &visitor) const override;
    fef::Blueprint::UP createInstance() const override;
    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().desc().indexField(fef::ParameterCollection::ANY).number();
    }
    bool setup(const fef::IIndexEnvironment & env, const fef::ParameterList & params) override;

private:
    uint32_t _fieldId;
    uint32_t _termId;
};

}
