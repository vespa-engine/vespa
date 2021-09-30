// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "termdistancefeature.h"
#include "valuefeature.h"
#include "utils.h"
#include <vespa/searchlib/fef/fieldinfo.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/vespalib/util/stash.h>

using namespace search::fef;

namespace search::features {


TermDistanceExecutor::TermDistanceExecutor(const IQueryEnvironment & env,
                                           const TermDistanceParams & params) :
    FeatureExecutor(),
    _termA(env.getTerm(params.termX)),
    _termB(env.getTerm(params.termY)),
    _md(nullptr)
{
    _termA.fieldHandle(util::getTermFieldData(env, params.termX, params.fieldId));
    _termB.fieldHandle(util::getTermFieldData(env, params.termY, params.fieldId));
}

bool TermDistanceExecutor::valid() const
{
    return ((_termA.termData() != 0) && (_termB.termData() != 0) &&
            (_termA.fieldHandle() != IllegalHandle) && (_termB.fieldHandle() != IllegalHandle));
}

void
TermDistanceExecutor::execute(uint32_t docId)
{
    TermDistanceCalculator::Result result;
    TermDistanceCalculator::run(_termA, _termB, *_md, docId, result);
    outputs().set_number(0, result.forwardDist);
    outputs().set_number(1, result.forwardTermPos);
    outputs().set_number(2, result.reverseDist);
    outputs().set_number(3, result.reverseTermPos);
}

void
TermDistanceExecutor::handle_bind_match_data(const fef::MatchData &md)
{
    _md = &md;
}

TermDistanceBlueprint::TermDistanceBlueprint() :
    Blueprint("termDistance"),
    _params()
{
}

void
TermDistanceBlueprint::visitDumpFeatures(const IIndexEnvironment &,
                                         IDumpFeatureVisitor &) const
{
}

Blueprint::UP
TermDistanceBlueprint::createInstance() const
{
    return std::make_unique<TermDistanceBlueprint>();
}

bool
TermDistanceBlueprint::setup(const IIndexEnvironment &,
                             const ParameterList & params)
{
    _params.fieldId = params[0].asField()->id();
    _params.termX = params[1].asInteger();
    _params.termY = params[2].asInteger();

    describeOutput("forward",             "the min distance between term X and term Y in the field");
    describeOutput("forwardTermPosition", "the position of term X for the forward distance");
    describeOutput("reverse",             "the min distance between term Y and term X in the field");
    describeOutput("reverseTermPosition", "the position of term Y for the reverse distance");

    return true;
}

FeatureExecutor &
TermDistanceBlueprint::createExecutor(const IQueryEnvironment &env, vespalib::Stash &stash) const
{
    TermDistanceExecutor &tde(stash.create<TermDistanceExecutor>(env, _params));
    if (tde.valid()) {
        return tde;
    } else {
        TermDistanceCalculator::Result r;
        std::vector<feature_t> values(4);
        values[0] = r.forwardDist;
        values[1] = r.forwardTermPos;
        values[2] = r.reverseDist;
        values[3] = r.reverseTermPos;
        return stash.create<ValueExecutor>(values);
    }
}

}
