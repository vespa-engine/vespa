// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
#include <cmath>
LOG_SETUP(".features.randomnormalfeature");
#include <vespa/searchlib/fef/properties.h>
#include "random_normal_feature.h"
#include "utils.h"

namespace search {
namespace features {

RandomNormalExecutor::RandomNormalExecutor(uint64_t seed, double mean, double stddev) :
    search::fef::FeatureExecutor(),
    _rnd(),
    _mean(mean),
    _stddev(stddev),
    _hasSpare(false),
    _spare(0.0)

{
    LOG(debug, "RandomNormalExecutor: seed=%" PRIu64 ", mean=%f, stddev=%f",
        seed, mean, stddev);
    _rnd.srand48(seed);
}

/**
 * Draws a random number from the Gaussian distribution
 * using the Marsaglia polar method.
 */
void
RandomNormalExecutor::execute(uint32_t)
{
    feature_t result = _spare;
    if (_hasSpare) {
        _hasSpare = false;
    } else {
        _hasSpare = true;

        feature_t u, v, s;
        do {
            u = (_rnd.lrand48() / (feature_t)0x80000000u) * 2.0 - 1.0;
            v = (_rnd.lrand48() / (feature_t)0x80000000u) * 2.0 - 1.0;
            s = u * u + v * v;
        } while ( (s >= 1.0) || (s == 0.0) );
        s = std::sqrt(-2.0 * std::log(s) / s);

        _spare = v * s; // saved for next invocation
        result = u * s;
    }

    outputs().set_number(0, _mean + _stddev * result);
}


RandomNormalBlueprint::RandomNormalBlueprint() :
    search::fef::Blueprint("randomNormal"),
    _seed(0),
    _mean(0.0),
    _stddev(1.0)
{
}

void
RandomNormalBlueprint::visitDumpFeatures(const search::fef::IIndexEnvironment &,
                                   search::fef::IDumpFeatureVisitor &) const
{
}

search::fef::Blueprint::UP
RandomNormalBlueprint::createInstance() const
{
    return search::fef::Blueprint::UP(new RandomNormalBlueprint());
}

bool
RandomNormalBlueprint::setup(const search::fef::IIndexEnvironment & env,
                       const search::fef::ParameterList & params)
{
    search::fef::Property p = env.getProperties().lookup(getName(), "seed");
    if (p.found()) {
        _seed = util::strToNum<uint64_t>(p.get());
    }

    if (params.size() > 0) {
        _mean = params[0].asDouble();
    }
    if (params.size() > 1) {
        _stddev = params[1].asDouble();
    }

    describeOutput("out" , "A random value drawn from the Gaussian distribution");

    return true;
}

search::fef::FeatureExecutor &
RandomNormalBlueprint::createExecutor(const search::fef::IQueryEnvironment &, vespalib::Stash &stash) const
{
    uint64_t seed = _seed;
    if (seed == 0) {
        FastOS_Time time;
        time.SetNow();
        seed = static_cast<uint64_t>(time.MicroSecs()) ^
                reinterpret_cast<uint64_t>(&seed); // results in different seeds in different threads
    }
    return stash.create<RandomNormalExecutor>(seed, _mean, _stddev);
}


} // namespace features
} // namespace search
