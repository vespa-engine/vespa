// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>
#include <vector>
#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/searchlib/util/rand48.h>

namespace search {
namespace features {


/**
 * Implements the executor for the random normal feature outputting a
 * random number drawn from the Gaussian distribution with the
 * two arguments 'mean' and 'stddev'.
 **/
class RandomNormalExecutor : public search::fef::FeatureExecutor {
private:
    Rand48 _rnd;
    double _mean;
    double _stddev;

    bool _hasSpare;
    double _spare;

public:
    /**
     * Constructs a new executor.
     **/
    RandomNormalExecutor(uint64_t seed, double mean, double stddev);
    virtual void execute(uint32_t docId);
};


/**
 * Implements the blueprint for the random normal feature.
 */
class RandomNormalBlueprint : public search::fef::Blueprint {
private:
    uint64_t _seed;
    double   _mean;
    double   _stddev;

public:
    /**
     * Constructs a new blueprint.
     */
    RandomNormalBlueprint();

    // Inherit doc from Blueprint.
    virtual void visitDumpFeatures(const search::fef::IIndexEnvironment & env,
                                   search::fef::IDumpFeatureVisitor & visitor) const;

    // Inherit doc from Blueprint.
    virtual search::fef::Blueprint::UP createInstance() const;

    // Inherit doc from Blueprint.
    virtual search::fef::ParameterDescriptions getDescriptions() const {
        return search::fef::ParameterDescriptions().
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

    // Inherit doc from Blueprint.
    virtual bool setup(const search::fef::IIndexEnvironment & env,
                       const search::fef::ParameterList & params);

    // Inherit doc from Blueprint.
    virtual search::fef::FeatureExecutor &createExecutor(const search::fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
};


} // namespace features
} // namespace search

