// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/features/fieldmatch/computer.h>
#include <vespa/searchlib/features/fieldmatch/params.h>

namespace search::features {

/**
 * Implements the executor for THE field match feature.
 */
class FieldMatchExecutor : public fef::FeatureExecutor {
private:
    fef::PhraseSplitter             _splitter;
    const fef::FieldInfo          & _field;
    const fieldmatch::Params              & _params;
    fieldmatch::Computer                    _cmp;

    void handle_bind_match_data(const fef::MatchData &md) override;

public:
    /**
     * Constructs an executor.
     */
    FieldMatchExecutor(const fef::IQueryEnvironment & queryEnv,
                       const fef::FieldInfo & field,
                       const fieldmatch::Params & params);
    void execute(uint32_t docId) override;
};


/**
 * Implements the blueprint for THE field match feature.
 */
class FieldMatchBlueprint : public fef::Blueprint {
private:
    const fef::FieldInfo * _field;
    fieldmatch::Params _params;

public:
    FieldMatchBlueprint();
    ~FieldMatchBlueprint();
    void visitDumpFeatures(const fef::IIndexEnvironment & env, fef::IDumpFeatureVisitor & visitor) const override;
    fef::Blueprint::UP createInstance() const override;
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().desc().indexField(fef::ParameterCollection::SINGLE);
    }

    bool setup(const fef::IIndexEnvironment & env, const fef::ParameterList & params) override;
    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
};

}
