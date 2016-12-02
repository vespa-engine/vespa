// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".features.reverseproximity");

#include <vespa/searchlib/fef/featurenamebuilder.h>
#include <vespa/searchlib/fef/fieldinfo.h>
#include <vespa/searchlib/fef/fieldtype.h>
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/vespalib/util/stringfmt.h>
#include "reverseproximityfeature.h"
#include "utils.h"

namespace search {
namespace features {

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
    _termB(util::getTermFieldHandle(env, _config.termB, _config.fieldId))
{
}

void
ReverseProximityExecutor::execute(search::fef::MatchData &match)
{
    // Cannot calculate proximity in this case
    if (_termA == search::fef::IllegalHandle || _termB == search::fef::IllegalHandle) {
        *match.resolveFeature(outputs()[0]) = util::FEATURE_MAX; // out
        *match.resolveFeature(outputs()[1]) = util::FEATURE_MIN; // posA
        *match.resolveFeature(outputs()[2]) = util::FEATURE_MAX; // posB
        return;
    }

    // Look for an initial pair to use as guess.
    uint32_t posA = 0, posB = 0;
    search::fef::FieldPositionsIterator itA, itB;
    search::fef::TermFieldMatchData &matchA = *match.resolveTermField(_termA);
    search::fef::TermFieldMatchData &matchB = *match.resolveTermField(_termB);
    if (matchA.getDocId() == match.getDocId() && matchB.getDocId() == match.getDocId()) {
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
    //LOG(debug, "Initial guess; posA is '%u' and posB is '%u'.", posA, posB);

    // _P_A_R_A_N_O_I_A_
    if (!itA.valid() || !itB.valid()) {
        //LOG(debug, "Initial guess is invalid.");
        *match.resolveFeature(outputs()[0]) = util::FEATURE_MAX; // out
        *match.resolveFeature(outputs()[1]) = util::FEATURE_MIN; // posA
        *match.resolveFeature(outputs()[2]) = util::FEATURE_MAX; // posB
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
    *match.resolveFeature(outputs()[0]) = optA - optB;
    *match.resolveFeature(outputs()[1]) = optA;
    *match.resolveFeature(outputs()[2]) = optB;
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
ReverseProximityBlueprint::setup(const search::fef::IIndexEnvironment &env,
                                 const search::fef::ParameterList &params)
{
    _config.fieldId = params[0].asField()->id();
    _config.termA = params[1].asInteger();
    _config.termB = params[2].asInteger();
    describeOutput("out" , "The reverse proximity of the query terms.");
    describeOutput("posA", "The best position of the first query term.");
    describeOutput("posB", "The best position of the second query term.");
    env.hintFieldAccess(_config.fieldId);
    return true;
}

search::fef::Blueprint::UP
ReverseProximityBlueprint::createInstance() const
{
    return search::fef::Blueprint::UP(new ReverseProximityBlueprint());
}

search::fef::FeatureExecutor &
ReverseProximityBlueprint::createExecutor(const search::fef::IQueryEnvironment &env, vespalib::Stash &stash) const
{
    return stash.create<ReverseProximityExecutor>(env, _config);
}

}}
