// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/fef/blueprint.h>

namespace search::features {

/**
 * Implements the executor for field length.
 */
class FieldLengthExecutor : public fef::FeatureExecutor {
private:
    std::vector<fef::TermFieldHandle> _fieldHandles;
    const fef::MatchData             *_md;

    void handle_bind_match_data(const fef::MatchData &md) override;

public:
    /**
     * Constructs an executor for field length.
     *
     * @param env       The query environment
     * @param fieldId   The field id
     */
    FieldLengthExecutor(const fef::IQueryEnvironment &env, uint32_t fieldId);
    void execute(uint32_t docId) override;
};

/**
 * Implements the blueprint for field length.
 */
class FieldLengthBlueprint : public fef::Blueprint {
private:
    const fef::FieldInfo *_field;

public:
    FieldLengthBlueprint();

    void visitDumpFeatures(const fef::IIndexEnvironment &env, fef::IDumpFeatureVisitor &visitor) const override;
    fef::Blueprint::UP createInstance() const override;
    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().desc().indexField(fef::ParameterCollection::SINGLE);
    }

    bool setup(const fef::IIndexEnvironment & env, const fef::ParameterList & params) override;
};

}
