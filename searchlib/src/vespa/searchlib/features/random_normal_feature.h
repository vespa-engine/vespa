// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/searchlib/util/random_normal.h>

namespace search {
namespace features {


/**
 * Implements the executor for the random normal feature outputting a
 * random number drawn from the Gaussian distribution with the
 * two arguments 'mean' and 'stddev'.
 **/
class RandomNormalExecutor : public fef::FeatureExecutor {
private:
    RandomNormal _rnd;       // seeded once per query

public:
    RandomNormalExecutor(uint64_t seed, double mean, double stddev);
    void execute(uint32_t docId) override;
};


/**
 * Implements the blueprint for the random normal feature.
 */
class RandomNormalBlueprint : public fef::Blueprint {
private:
    uint64_t _seed;
    double   _mean;
    double   _stddev;

public:
    RandomNormalBlueprint();

    void visitDumpFeatures(const fef::IIndexEnvironment & env, fef::IDumpFeatureVisitor & visitor) const override;
    fef::Blueprint::UP createInstance() const override;
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().
            // Can run without parameters:
            desc().

            // Can run with two parameters (mean and stddev):
            desc().
            number(). // mean
            number(). // stddev

            // Can run with three parameters:
            desc().
            number(). // mean
            number(). // stddev
            string(); // in order to name different features
    }

    bool setup(const fef::IIndexEnvironment & env, const fef::ParameterList & params) override;
    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
};


} // namespace features
} // namespace search

