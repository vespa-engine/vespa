// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <string>
#include <vector>
#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>

namespace search {
namespace features {

/**
 * Implements the executor for the 'now' feature. This executor returns the current
 * system time, or the time specified by the query argument 'vespa.now'.
 * Time is returned in two formats. First as seconds since epoch (first output),
 * then as days since epoch and seconds within that day (second and third output).
 * This is due to precision problems when encoding current time as a float.
 **/
class NowExecutor : public search::fef::FeatureExecutor {
private:
    // Current time, in seconds since epoch
    int64_t _timestamp;

public:
    /**
     * Constructs a new executor.
     **/
    NowExecutor(int64_t timestamp);
    virtual void execute(search::fef::MatchData & data);
};

/**
 * Implements the blueprint for 'now' feature.
 */
class NowBlueprint : public search::fef::Blueprint {
public:
    NowBlueprint() : search::fef::Blueprint("now") { }

    // Inherit doc from Blueprint.
    virtual void visitDumpFeatures(const search::fef::IIndexEnvironment &env,
                                   search::fef::IDumpFeatureVisitor &visitor) const;

    // Inherit doc from Blueprint.
    virtual search::fef::Blueprint::UP createInstance() const;

    // Inherit doc from Blueprint.
    virtual search::fef::ParameterDescriptions getDescriptions() const {
        return search::fef::ParameterDescriptions().desc();
    }

    // Inherit doc from Blueprint.
    virtual bool setup(const search::fef::IIndexEnvironment & env,
                       const search::fef::ParameterList & params);

    // Inherit doc from Blueprint.
    virtual search::fef::FeatureExecutor &createExecutor(const search::fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
};

}}

