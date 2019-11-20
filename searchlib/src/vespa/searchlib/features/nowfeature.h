// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/fef/blueprint.h>

namespace search::features {

/**
 * Implements the executor for the 'now' feature. This executor returns the current
 * system time, or the time specified by the query argument 'vespa.now'.
 * Time is returned in two formats. First as seconds since epoch (first output),
 * then as days since epoch and seconds within that day (second and third output).
 * This is due to precision problems when encoding current time as a float.
 **/
class NowExecutor : public fef::FeatureExecutor {
private:
    // Current time, in seconds since epoch
    int64_t _timestamp;

public:
    /**
     * Constructs a new executor.
     **/
    NowExecutor(int64_t timestamp);
    void execute(uint32_t docId) override;
};

/**
 * Implements the blueprint for 'now' feature.
 */
class NowBlueprint : public fef::Blueprint {
public:
    NowBlueprint() : fef::Blueprint("now") { }
    void visitDumpFeatures(const fef::IIndexEnvironment &env, fef::IDumpFeatureVisitor &visitor) const override;
    fef::Blueprint::UP createInstance() const override;
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().desc();
    }
    bool setup(const fef::IIndexEnvironment & env, const fef::ParameterList & params) override;
    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
};

}
