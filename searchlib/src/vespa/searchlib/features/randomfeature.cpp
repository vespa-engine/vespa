// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "randomfeature.h"
#include "utils.h"
#include <vespa/searchlib/fef/properties.h>
#include <vespa/vespalib/util/stash.h>
#include <chrono>
#include <cinttypes>

#include <vespa/log/log.h>
LOG_SETUP(".features.randomfeature");

namespace search::features {

RandomExecutor::RandomExecutor(uint64_t seed, uint64_t matchSeed)
    : fef::FeatureExecutor(),
      _rnd(),
      _matchRnd(),
      _matchSeed(matchSeed)
{
    LOG(debug, "RandomExecutor: seed=%" PRIu64 ", matchSeed=%" PRIu64, seed, matchSeed);
    _rnd.srand48(seed);
}

void
RandomExecutor::execute(uint32_t docId)
{
    feature_t rndScore = _rnd.lrand48() / (feature_t)0x80000000u; // 2^31
    _matchRnd.srand48(_matchSeed + docId);
    feature_t matchRndScore = _matchRnd.lrand48() / (feature_t)0x80000000u; // 2^31
    outputs().set_number(0, rndScore);
    outputs().set_number(1, matchRndScore);
}


RandomBlueprint::RandomBlueprint() :
    fef::Blueprint("random"),
    _seed(0)
{
}

void
RandomBlueprint::visitDumpFeatures(const fef::IIndexEnvironment &, fef::IDumpFeatureVisitor &) const
{
}

fef::Blueprint::UP
RandomBlueprint::createInstance() const
{
    return std::make_unique<RandomBlueprint>();
}

bool
RandomBlueprint::setup(const fef::IIndexEnvironment & env, const fef::ParameterList &)
{
    fef::Property p = env.getProperties().lookup(getName(), "seed");
    if (p.found()) {
        _seed = util::strToNum<uint64_t>(p.get());
    }
    describeOutput("out" , "A random value in the interval [0, 1>");
    describeOutput("match" , "A random value in the interval [0, 1> that is stable for a given match (document and query)");
    return true;
}

using namespace std::chrono;

fef::FeatureExecutor &
RandomBlueprint::createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const
{
    uint64_t seed = _seed;
    if (seed == 0) {
        seed = static_cast<uint64_t>(duration_cast<microseconds>(system_clock::now().time_since_epoch()).count()) ^
                reinterpret_cast<uint64_t>(&seed); // results in different seeds in different threads
    }
    uint64_t matchSeed = util::strToNum<uint64_t>
        (env.getProperties().lookup(getName(), "match", "seed").get("1024")); // default seed

    return stash.create<RandomExecutor>(seed, matchSeed);
}

}
