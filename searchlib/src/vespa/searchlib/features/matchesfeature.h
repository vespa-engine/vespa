// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>

namespace search::features {

/**
 * Implements the blueprint for the matches executor.
 *
 * matches(name)
 *  - returns 1 if there is an index or attribute with this name which matched the query, 0 otherwise
 * matches(name,n)
 *  - returns 1 if there is an index or attribute with this name which matched with the query term at the given position, 0 otherwise
 */
class MatchesBlueprint : public fef::Blueprint
{
private:
    const fef::FieldInfo *_field;
    uint32_t              _termIdx;

public:
    MatchesBlueprint();
    void visitDumpFeatures(const fef::IIndexEnvironment & env, fef::IDumpFeatureVisitor & visitor) const override;
    fef::Blueprint::UP createInstance() const override;
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().desc().field().desc().field().number();
    }
    bool setup(const fef::IIndexEnvironment & env, const fef::ParameterList & params) override;
    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
};

}
