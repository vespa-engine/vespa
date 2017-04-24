// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/searchlib/fef/table.h>
#include "termdistancecalculator.h"

namespace search {
namespace features {

/**
 * This struct contains parameters used by the executor.
 **/
struct TermDistanceParams {
    uint32_t fieldId;
    uint32_t termX;
    uint32_t termY;
    TermDistanceParams() : fieldId(0), termX(0), termY(0) {}
};

/**
 * Implements the executor for calculating min term distance (forward and reverse).
 **/
class TermDistanceExecutor : public search::fef::FeatureExecutor
{
private:
    const TermDistanceParams & _params;
    QueryTerm                  _termA;
    QueryTerm                  _termB;
    const fef::MatchData      *_md;

    virtual void handle_bind_match_data(fef::MatchData &md) override;

public:
    TermDistanceExecutor(const search::fef::IQueryEnvironment & env,
                         const TermDistanceParams & params);
    virtual void execute(uint32_t docId) override;
    bool valid() const;
};


/**
 * Implements the blueprint for the term distance executor.
 **/
class TermDistanceBlueprint : public search::fef::Blueprint {
private:
    TermDistanceParams _params;

public:
    TermDistanceBlueprint();

    // Inherit doc from Blueprint.
    virtual void visitDumpFeatures(const search::fef::IIndexEnvironment & env,
                                   search::fef::IDumpFeatureVisitor & visitor) const override;

    // Inherit doc from Blueprint.
    virtual search::fef::Blueprint::UP createInstance() const override;

    // Inherit doc from Blueprint.
    virtual search::fef::ParameterDescriptions getDescriptions() const override {
        return search::fef::ParameterDescriptions().desc().indexField(search::fef::ParameterCollection::ANY).number().number();
    }

    // Inherit doc from Blueprint.
    virtual bool setup(const search::fef::IIndexEnvironment & env,
                       const search::fef::ParameterList & params) override;

    // Inherit doc from Blueprint.
    virtual search::fef::FeatureExecutor &createExecutor(const search::fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
};


} // namespace features
} // namespace search

