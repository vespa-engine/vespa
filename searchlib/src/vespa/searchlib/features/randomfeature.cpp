// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "randomfeature.h"
#include "utils.h"
#include <vespa/searchlib/fef/properties.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/fastos/time.h>

#include <vespa/log/log.h>
LOG_SETUP(".features.randomfeature");

namespace search {
namespace features {

RandomExecutor::RandomExecutor(uint64_t seed, uint64_t matchSeed) :
    search::fef::FeatureExecutor(),
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
    search::fef::Blueprint("random"),
    _seed(0)
{
}

void
RandomBlueprint::visitDumpFeatures(const search::fef::IIndexEnvironment &,
                                   search::fef::IDumpFeatureVisitor &) const
{
}

search::fef::Blueprint::UP
RandomBlueprint::createInstance() const
{
    return search::fef::Blueprint::UP(new RandomBlueprint());
}

bool
RandomBlueprint::setup(const search::fef::IIndexEnvironment & env,
                       const search::fef::ParameterList &)
{
    search::fef::Property p = env.getProperties().lookup(getName(), "seed");
    if (p.found()) {
        _seed = util::strToNum<uint64_t>(p.get());
    }
    describeOutput("out" , "A random value in the interval [0, 1>");
    describeOutput("match" , "A random value in the interval [0, 1> that is stable for a given match (document and query)");
    return true;
}

search::fef::FeatureExecutor &
RandomBlueprint::createExecutor(const search::fef::IQueryEnvironment &env, vespalib::Stash &stash) const
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

    return stash.create<RandomExecutor>(seed, matchSeed);
}


} // namespace features
} // namespace search
