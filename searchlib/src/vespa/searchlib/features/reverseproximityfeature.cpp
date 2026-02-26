// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "reverseproximityfeature.h"
#include "utils.h"
#include <vespa/searchlib/fef/fieldinfo.h>
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/vespalib/util/stash.h>

namespace search::features {

ReverseProximityConfig::ReverseProximityConfig() :
    fieldId(search::fef::IllegalHandle),
    termA(std::numeric_limits<uint32_t>::max()),
    termB(std::numeric_limits<uint32_t>::max())
{
    // empty
}

ReverseProximityExecutor::ReverseProximityExecutor(const search::fef::IQueryEnvironment &env,
                                                   const ReverseProximityConfig &config) :
    search::fef::FeatureExecutor(),
    _config(config),
    _termA(util::getTermFieldHandle(env, _config.termA, _config.fieldId)),
    _termB(util::getTermFieldHandle(env, _config.termB, _config.fieldId)),
    _md(nullptr)
{
}

void
ReverseProximityExecutor::execute(uint32_t docId)
{
    // Cannot calculate proximity in this case
    if (_termA == search::fef::IllegalHandle || _termB == search::fef::IllegalHandle) {
        outputs().set_number(0, util::FEATURE_MAX); // out
        outputs().set_number(1, util::FEATURE_MIN); // posA
        outputs().set_number(2, util::FEATURE_MAX); // posB
        return;
    }

    // Look for an initial pair to use as guess.
    uint32_t posA = 0, posB = 0;
    search::fef::FieldPositionsIterator itA, itB;
    const fef::TermFieldMatchData &matchA = *_md->resolveTermField(_termA);
    const fef::TermFieldMatchData &matchB = *_md->resolveTermField(_termB);
    if (matchA.has_ranking_data(docId) && matchB.has_ranking_data(docId)) {
        itA = matchA.getIterator();
        itB = matchB.getIterator();
        if (itA.valid() && itB.valid()) {
            for(posA = itA.getPosition(), posB = itB.getPosition();
                itA.valid() && itA.getPosition() < posB; itA.next())
            {
                // empty
            }
        }
    }

    // _P_A_R_A_N_O_I_A_
    if (!itA.valid() || !itB.valid()) {
        outputs().set_number(0, util::FEATURE_MAX); // out
        outputs().set_number(1, util::FEATURE_MIN); // posA
        outputs().set_number(2, util::FEATURE_MAX); // posB
        return;
    }

    // Look for optimal positions for term A and B.
    uint32_t optA = posA, optB = posB;
    while (itA.valid() && itB.valid()) {
        uint32_t a = itA.getPosition(), b = itB.getPosition();
        if (b < posA) {
            posB = b;
            itB.next();
        }
        else {
            if (posA - posB < optA - optB) {
                optA = posA;
                optB = posB;
            }
            posA = a;
            itA.next();
        }
    }

    // Output proximity score.
    outputs().set_number(0, optA - optB);
    outputs().set_number(1, optA);
    outputs().set_number(2, optB);
}

void
ReverseProximityExecutor::handle_bind_match_data(const fef::MatchData &md)
{
    _md = &md;
}

ReverseProximityBlueprint::ReverseProximityBlueprint() :
    search::fef::Blueprint("reverseProximity"),
    _config()
{
    // empty
}

void
ReverseProximityBlueprint::visitDumpFeatures(const search::fef::IIndexEnvironment &,
                                             search::fef::IDumpFeatureVisitor &) const
{
    // empty
}

bool
ReverseProximityBlueprint::setup(const search::fef::IIndexEnvironment &,
                                 const search::fef::ParameterList &params)
{
    _config.fieldId = params[0].asField()->id();
    _config.termA = params[1].asInteger();
    _config.termB = params[2].asInteger();
    describeOutput("out" , "The reverse proximity of the query terms.");
    describeOutput("posA", "The best position of the first query term.");
    describeOutput("posB", "The best position of the second query term.");
    return true;
}

search::fef::Blueprint::UP
ReverseProximityBlueprint::createInstance() const
{
    return std::make_unique<ReverseProximityBlueprint>();
}

search::fef::FeatureExecutor &
ReverseProximityBlueprint::createExecutor(const search::fef::IQueryEnvironment &env, vespalib::Stash &stash) const
{
    return stash.create<ReverseProximityExecutor>(env, _config);
}

}
