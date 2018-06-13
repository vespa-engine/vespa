// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "random_normal_feature.h"
#include "utils.h"
#include <vespa/searchlib/fef/properties.h>
#include <vespa/fastos/time.h>
#include <cmath>

#include <vespa/log/log.h>
LOG_SETUP(".features.randomnormalfeature");

namespace search {
namespace features {

RandomNormalExecutor::RandomNormalExecutor(uint64_t seed, uint64_t matchSeed, double mean, double stddev) :
    search::fef::FeatureExecutor(),
    _rnd(),
    _matchRnd(),
    _matchSeed(matchSeed),
    _mean(mean),
    _stddev(stddev),
    _hasSpare(false),
    _spare(0.0)
{
    LOG(debug, "RandomNormalExecutor: seed=%zu, matchSeed=%zu, mean=%f, stddev=%f", seed, matchSeed, mean, stddev);
    _rnd.srand48(seed);
}

feature_t generateRandom(Rand48 generator) {
    return (generator.lrand48() / (feature_t)0x80000000u) * 2.0 - 1.0;
}

/**
 * Draws a random number from the Gaussian distribution
 * using the Marsaglia polar method.
 */
void
RandomNormalExecutor::execute(uint32_t docId)
{
    feature_t result = _spare;
    if (_hasSpare) {
        _hasSpare = false;
    } else {
        _hasSpare = true;

        feature_t u, v, s;
        do {
            u = generateRandom(_rnd);
            v = generateRandom(_rnd);
            s = u * u + v * v;
        } while ( (s >= 1.0) || (s == 0.0) );
        s = std::sqrt(-2.0 * std::log(s) / s);

        _spare = v * s; // saved for next invocation
        result = u * s;
    }
    outputs().set_number(0, _mean + _stddev * result);

    _matchRnd.srand48(_matchSeed + docId);
    feature_t u, v, s;
    do {
        u = generateRandom(_matchRnd);
        v = generateRandom(_matchRnd);
        s = u * u + v * v;
    } while ( (s >= 1.0) || (s == 0.0) );
    s = std::sqrt(-2.0 * std::log(s) / s);
    outputs().set_number(1, _mean + _stddev * u * s);
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
    describeOutput("match" , "A random value drawn from the Gaussian distribution that is stable for a given match (document and query)");

    return true;
}

search::fef::FeatureExecutor &
RandomNormalBlueprint::createExecutor(const search::fef::IQueryEnvironment &env, vespalib::Stash &stash) const
{
    uint64_t seed = _seed;
    if (seed == 0) {
        FastOS_Time time;
        time.SetNow();
        seed = static_cast<uint64_t>(time.MicroSecs()) ^
                reinterpret_cast<uint64_t>(&seed); // results in different seeds in different threads
    }
    uint64_t matchSeed = util::strToNum<uint64_t>
            (env.getProperties().lookup(getName(), "match", "seed").get("1024")); // default seed
    return stash.create<RandomNormalExecutor>(seed, matchSeed, _mean, _stddev);
}


} // namespace features
} // namespace search
