// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "random_normal_feature.h"
#include "utils.h"
#include <vespa/searchlib/fef/properties.h>
#include <vespa/vespalib/util/stash.h>
#include <chrono>
#include <cinttypes>

#include <vespa/log/log.h>
LOG_SETUP(".features.randomnormalfeature");

namespace search::features {

RandomNormalExecutor::RandomNormalExecutor(uint64_t seed, double mean, double stddev)
    : fef::FeatureExecutor(),
      _rnd(mean, stddev, true)
{
    LOG(debug, "RandomNormalExecutor: seed=%" PRIu64 ", mean=%f, stddev=%f", seed, mean, stddev);
    _rnd.seed(seed);
}

void
RandomNormalExecutor::execute(uint32_t)
{
    outputs().set_number(0, _rnd.next());
}

RandomNormalBlueprint::RandomNormalBlueprint() :
    fef::Blueprint("randomNormal"),
    _seed(0),
    _mean(0.0),
    _stddev(1.0)
{
}

void
RandomNormalBlueprint::visitDumpFeatures(const fef::IIndexEnvironment &, fef::IDumpFeatureVisitor &) const
{
}

fef::Blueprint::UP
RandomNormalBlueprint::createInstance() const
{
    return std::make_unique<RandomNormalBlueprint>();
}

bool
RandomNormalBlueprint::setup(const fef::IIndexEnvironment & env, const fef::ParameterList & params)
{
    fef::Property p = env.getProperties().lookup(getName(), "seed");
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

using namespace std::chrono;

fef::FeatureExecutor &
RandomNormalBlueprint::createExecutor(const fef::IQueryEnvironment &, vespalib::Stash &stash) const
{
    uint64_t seed = _seed;
    if (seed == 0) {
        seed = static_cast<uint64_t>(duration_cast<microseconds>(system_clock::now().time_since_epoch()).count()) ^
                reinterpret_cast<uint64_t>(&seed); // results in different seeds in different threads
    }
    return stash.create<RandomNormalExecutor>(seed, _mean, _stddev);
}

}
