// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>

namespace search {
namespace features {

/**
 * Implements the executor for the matches feature for index and
 * attribute fields.
 */
class CountMatchesExecutor : public fef::FeatureExecutor
{
private:
    std::vector<fef::TermFieldHandle> _handles;

public:
    CountMatchesExecutor(uint32_t fieldId, const fef::IQueryEnvironment &env);
    void execute(fef::MatchData & data) override;
};

/**
 * Implements the blueprint for the matches executor.
 *
 * matches(name)
 *  - returns 1 if there is an index or attribute with this name which matched the query, 0 otherwise
 * matches(name,n)
 *  - returns 1 if there is an index or attribute with this name which matched with the query term at the given position, 0 otherwise
 */
class CountMatchesBlueprint : public fef::Blueprint
{
private:
    const fef::FieldInfo *_field;

public:
    /**
     * Constructs a blueprint.
     */
    CountMatchesBlueprint();

    // Inherit doc from Blueprint.
    void visitDumpFeatures(const fef::IIndexEnvironment & env,
                           fef::IDumpFeatureVisitor & visitor) const override;

    // Inherit doc from Blueprint.
    fef::Blueprint::UP createInstance() const override;

    // Inherit doc from Blueprint.
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().desc().field();
    }

    // Inherit doc from Blueprint.
    bool setup(const fef::IIndexEnvironment & env,
               const fef::ParameterList & params) override;

    // Inherit doc from Blueprint.
    fef::FeatureExecutor::LP createExecutor(const fef::IQueryEnvironment & env) const override;
};

} // namespace features
} // namespace search

