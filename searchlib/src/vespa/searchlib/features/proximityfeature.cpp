// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "proximityfeature.h"
#include "utils.h"
#include <vespa/searchlib/fef/fieldinfo.h>
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/vespalib/util/stash.h>

namespace search::features {

ProximityConfig::ProximityConfig() :
    fieldId(search::fef::IllegalHandle),
    termA(std::numeric_limits<uint32_t>::max()),
    termB(std::numeric_limits<uint32_t>::max())
{
    // empty
}

ProximityExecutor::ProximityExecutor(const search::fef::IQueryEnvironment &env,
                                     const ProximityConfig &config) :
    search::fef::FeatureExecutor(),
    _config(config),
    _termA(util::getTermFieldHandle(env, _config.termA, _config.fieldId)),
    _termB(util::getTermFieldHandle(env, _config.termB, _config.fieldId))
{
}

void
ProximityExecutor::execute(uint32_t docId)
{
    // Cannot calculate proximity in this case
    if (_termA != search::fef::IllegalHandle &&
        _termB != search::fef::IllegalHandle)
    {
        const fef::TermFieldMatchData &matchA = *_md->resolveTermField(_termA);
        const fef::TermFieldMatchData &matchB = *_md->resolveTermField(_termB);

        if (matchA.has_ranking_data(docId) && matchB.has_ranking_data(docId)) {
            if (findBest(matchA, matchB)) return;
        }
    }
    // no match
    outputs().set_number(0, util::FEATURE_MAX); // out
    outputs().set_number(1, util::FEATURE_MAX); // posA
    outputs().set_number(2, util::FEATURE_MIN); // posB
    return;
}

void
ProximityExecutor::handle_bind_match_data(const fef::MatchData &md)
{
    _md = &md;
}

bool
ProximityExecutor::findBest(const fef::TermFieldMatchData &matchA,
                            const fef::TermFieldMatchData &matchB)
{
    // Look for optimal positions for term A and B.
    uint32_t optA = 0, optB = 0xFFFFFFFFu;

    search::fef::TermFieldMatchData::PositionsIterator itA, itB, epA, epB;
    itA = matchA.begin();
    itB = matchB.begin();
    epA = matchA.end();
    epB = matchB.end();

    while (itB != epB) {
        uint32_t eid = itB->getElementId();
        while (itA != epA && itA->getElementId() < eid) {
            ++itA;
        }
        if (itA != epA && itA->getElementId() == eid) {
            // there is a pair somewhere here
            while (itA != epA &&
                   itB != epB &&
                   itA->getElementId() == eid &&
                   itB->getElementId() == eid)
            {
                uint32_t a = itA->getPosition();
                uint32_t b = itB->getPosition();
                if (a < b) {
                    if (b - a < optB - optA) {
                        optA = a;
                        optB = b;
                    }
                    ++itA;
                } else {
                    ++itB;
                }
            }
        } else {
            ++itB;
        }
    }
    if (optB != 0xFFFFFFFFu) {
        // Output proximity score.
        outputs().set_number(0, optB - optA);
        outputs().set_number(1, optA);
        outputs().set_number(2, optB);
        return true;
    } else {
        return false;
    }
}

ProximityBlueprint::ProximityBlueprint() :
    search::fef::Blueprint("proximity"),
    _config()
{
    // empty
}

void
ProximityBlueprint::visitDumpFeatures(const search::fef::IIndexEnvironment &,
                                      search::fef::IDumpFeatureVisitor &) const
{
    // empty
}

bool
ProximityBlueprint::setup(const search::fef::IIndexEnvironment&,
                          const search::fef::ParameterList &params)
{
    _config.fieldId = params[0].asField()->id();
    _config.termA = params[1].asInteger();
    _config.termB = params[2].asInteger();
    describeOutput("out" , "The proximity of the query terms.");
    describeOutput("posA", "The best position of the first query term.");
    describeOutput("posB", "The best position of the second query term.");
    return true;
}

search::fef::Blueprint::UP
ProximityBlueprint::createInstance() const
{
    return std::make_unique<ProximityBlueprint>();
}

search::fef::FeatureExecutor &
ProximityBlueprint::createExecutor(const search::fef::IQueryEnvironment &env, vespalib::Stash &stash) const
{
    return stash.create<ProximityExecutor>(env, _config);
}

}
