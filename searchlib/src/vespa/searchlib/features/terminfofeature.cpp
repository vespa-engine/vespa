// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "terminfofeature.h"
#include "valuefeature.h"
#include <vespa/searchlib/fef/properties.h>
#include <vespa/searchlib/fef/featurenamebuilder.h>
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/vespalib/util/stash.h>

using namespace search::fef;
namespace search::features {

TermInfoBlueprint::TermInfoBlueprint()
    : Blueprint("termInfo"),
      _termIdx(0)
{
}

void
TermInfoBlueprint::visitDumpFeatures(const IIndexEnvironment &,
                                     IDumpFeatureVisitor &) const
{
}

bool
TermInfoBlueprint::setup(const IIndexEnvironment &,
                         const ParameterList & params)
{
    _termIdx = params[0].asInteger();
    describeOutput("queryidx", "The index of the first term with the given "
                   "term index in the query term ordering. -1 if not found.");
    return true;
}

FeatureExecutor &
TermInfoBlueprint::createExecutor(const IQueryEnvironment &queryEnv, vespalib::Stash &stash) const
{
    feature_t queryIdx = -1.0;
    if (queryEnv.getNumTerms() > _termIdx) {
        queryIdx = _termIdx;
    }
    return stash.create<SingleValueExecutor>(queryIdx);
}

}
