// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/vespalib/util/rand48.h>

namespace search::features {

/**
 * Implements the executor for the random feature outputting a number in the interval [0, 1>.
 **/
class RandomExecutor : public search::fef::FeatureExecutor {
private:
    vespalib::Rand48   _rnd;       // seeded once per query
    vespalib::Rand48   _matchRnd;  // seeded once per match
    uint64_t _matchSeed;

public:
    RandomExecutor(uint64_t seed, uint64_t matchSeed);
    void execute(uint32_t docId) override;
};

/**
 * Implements the blueprint for the random feature.
 */
class RandomBlueprint : public search::fef::Blueprint {
private:
    uint64_t _seed;

public:
    RandomBlueprint();
    void visitDumpFeatures(const search::fef::IIndexEnvironment & env, search::fef::IDumpFeatureVisitor & visitor) const override;
    search::fef::Blueprint::UP createInstance() const override;
    search::fef::ParameterDescriptions getDescriptions() const override {
        return search::fef::ParameterDescriptions().
            desc().
            desc().string(); // in order to name different features
    }

    bool setup(const search::fef::IIndexEnvironment & env, const search::fef::ParameterList & params) override;
    search::fef::FeatureExecutor &createExecutor(const search::fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
};

}
