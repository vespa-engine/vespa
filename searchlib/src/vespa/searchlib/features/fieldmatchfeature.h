// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/features/fieldmatch/params.h>

namespace search::features {

/**
 * Implements the blueprint for THE field match feature.
 */
class FieldMatchBlueprint : public fef::Blueprint {
private:
    const fef::FieldInfo * _field;
    vespalib::string _shared_state_key;
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

    void prepareSharedState(const fef::IQueryEnvironment &queryEnv, fef::IObjectStore &objectStore) const override;
};

}
