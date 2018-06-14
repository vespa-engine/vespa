// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "random_normal_feature.h"
#include "utils.h"
#include <vespa/searchlib/fef/properties.h>
#include <vespa/fastos/time.h>

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
    _stddev(stddev)
{
    LOG(debug, "RandomNormalExecutor: seed=%zu, matchSeed=%zu, mean=%f, stddev=%f", seed, matchSeed, mean, stddev);
    _rnd.seed(seed);
}

void
RandomNormalExecutor::execute(uint32_t docId)
{
    outputs().set_number(0, _mean + _stddev * _rnd.next());
    _matchRnd.seed(_matchSeed + docId);
    outputs().set_number(0, _mean + _stddev * _matchRnd.next(false));
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
