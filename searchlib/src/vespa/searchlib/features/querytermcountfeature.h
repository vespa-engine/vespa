// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>

namespace search::features {

/**
 * Implements the blueprint for the query term count feature.
 */
class QueryTermCountBlueprint : public search::fef::Blueprint {
private:
public:
    QueryTermCountBlueprint();

    void visitDumpFeatures(const search::fef::IIndexEnvironment & env, search::fef::IDumpFeatureVisitor & visitor) const override;
    search::fef::Blueprint::UP createInstance() const override;
    search::fef::ParameterDescriptions getDescriptions() const override {
        return search::fef::ParameterDescriptions().desc();
    }
    bool setup(const search::fef::IIndexEnvironment & env, const search::fef::ParameterList & params) override;
    search::fef::FeatureExecutor &createExecutor(const search::fef::IQueryEnvironment & env, vespalib::Stash &stash) const override;
};

}
