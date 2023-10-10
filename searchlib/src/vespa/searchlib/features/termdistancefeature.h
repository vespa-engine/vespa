// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/table.h>
#include "termdistancecalculator.h"

namespace search::features {

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
class TermDistanceExecutor : public fef::FeatureExecutor
{
private:
    QueryTerm        _termA;
    QueryTerm        _termB;
    const fef::MatchData *_md;

    virtual void handle_bind_match_data(const fef::MatchData &md) override;

public:
    TermDistanceExecutor(const fef::IQueryEnvironment & env,
                         const TermDistanceParams & params);
    void execute(uint32_t docId) override;
    bool valid() const;
};


/**
 * Implements the blueprint for the term distance executor.
 **/
class TermDistanceBlueprint : public fef::Blueprint {
private:
    TermDistanceParams _params;

public:
    TermDistanceBlueprint();
    void visitDumpFeatures(const fef::IIndexEnvironment & env, fef::IDumpFeatureVisitor & visitor) const override;
    fef::Blueprint::UP createInstance() const override;
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().desc().indexField(fef::ParameterCollection::ANY).number().number();
    }
    bool setup(const fef::IIndexEnvironment & env, const fef::ParameterList & params) override;
    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
};

}
