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
class MatchesExecutor : public search::fef::FeatureExecutor
{
private:
    std::vector<search::fef::TermFieldHandle> _handles;
    const fef::MatchData                     *_md;

    virtual void handle_bind_match_data(fef::MatchData &md) override;

public:
    MatchesExecutor(uint32_t fieldId,
                    const search::fef::IQueryEnvironment &env,
                    uint32_t begin, uint32_t end);
    virtual void execute(search::fef::MatchData & data);
};

/**
 * Implements the blueprint for the matches executor.
 *
 * matches(name)
 *  - returns 1 if there is an index or attribute with this name which matched the query, 0 otherwise
 * matches(name,n)
 *  - returns 1 if there is an index or attribute with this name which matched with the query term at the given position, 0 otherwise
 */
class MatchesBlueprint : public search::fef::Blueprint
{
private:
    const search::fef::FieldInfo *_field;
    uint32_t                      _termIdx;

public:
    /**
     * Constructs a blueprint.
     */
    MatchesBlueprint();

    // Inherit doc from Blueprint.
    virtual void visitDumpFeatures(const search::fef::IIndexEnvironment & env,
                                   search::fef::IDumpFeatureVisitor & visitor) const;

    // Inherit doc from Blueprint.
    virtual search::fef::Blueprint::UP createInstance() const;

    // Inherit doc from Blueprint.
    virtual search::fef::ParameterDescriptions getDescriptions() const {
        return search::fef::ParameterDescriptions().
            desc().field().
            desc().field().number();
    }

    // Inherit doc from Blueprint.
    virtual bool setup(const search::fef::IIndexEnvironment & env,
                       const search::fef::ParameterList & params);

    // Inherit doc from Blueprint.
    virtual search::fef::FeatureExecutor &createExecutor(const search::fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
};

} // namespace features
} // namespace search

