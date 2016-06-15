// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".features.termfeature");

#include <vespa/searchlib/fef/featurenamebuilder.h>
#include <vespa/searchlib/fef/fieldinfo.h>
#include <vespa/searchlib/fef/fieldtype.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/vespalib/util/stringfmt.h>
#include "termfeature.h"
#include "utils.h"

using namespace search::fef;

namespace search {
namespace features {

TermExecutor::TermExecutor(const search::fef::IQueryEnvironment &env,
                           uint32_t termId) :
    search::fef::FeatureExecutor(),
    _termData(env.getTerm(termId)),
    _connectedness(util::lookupConnectedness(env, termId)),
    _significance(0)
{
    if (_termData != NULL) {
        feature_t fallback = util::getSignificance(*_termData);
        _significance = util::lookupSignificance(env, termId, fallback);
    }
}

void
TermExecutor::execute(search::fef::MatchData &match)
{
    if (_termData == NULL) { // this query term is not present in the query
        *match.resolveFeature(outputs()[0]) = 0.0f; // connectedness
        *match.resolveFeature(outputs()[1]) = 0.0f; // significance (1 - frequency)
        *match.resolveFeature(outputs()[2]) = 0.0f; // weight
        return;
    }
    *match.resolveFeature(outputs()[0]) = _connectedness;
    *match.resolveFeature(outputs()[1]) = _significance;
    *match.resolveFeature(outputs()[2]) = (feature_t)_termData->getWeight().percent();
}

TermBlueprint::TermBlueprint() :
    search::fef::Blueprint("term"),
    _termId(0)
{
    // empty
}

void
TermBlueprint::visitDumpFeatures(const search::fef::IIndexEnvironment &env,
                                 search::fef::IDumpFeatureVisitor &visitor) const
{
    int numTerms = atoi(env.getProperties().lookup(getBaseName(), "numTerms").get("5").c_str());
    for (int term = 0; term < numTerms; ++term) {
        search::fef::FeatureNameBuilder fnb;
        fnb.baseName(getBaseName()).parameter(vespalib::make_string("%d", term));
        visitor.visitDumpFeature(fnb.output("connectedness").buildName());
        visitor.visitDumpFeature(fnb.output("significance").buildName());
        visitor.visitDumpFeature(fnb.output("weight").buildName());
    }
}

bool
TermBlueprint::setup(const search::fef::IIndexEnvironment &,
                     const search::fef::ParameterList &params)
{
    _termId = params[0].asInteger();
    describeOutput("connectedness", "The normalized strength with which this term is connected to the next term in the query.");
    describeOutput("significance",  "1 - the normalized frequency of documents containing this query term.");
    describeOutput("weight",        "The normalized importance of matching this query term.");
    return true;
}

search::fef::Blueprint::UP
TermBlueprint::createInstance() const
{
    return search::fef::Blueprint::UP(new TermBlueprint());
}

search::fef::FeatureExecutor::LP
TermBlueprint::createExecutor(const search::fef::IQueryEnvironment &env) const
{
    return search::fef::FeatureExecutor::LP(new TermExecutor(env, _termId));
}

}}
