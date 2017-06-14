// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "terminfofeature.h"
#include "valuefeature.h"
#include <vespa/searchlib/fef/properties.h>
#include <vespa/searchlib/fef/fieldinfo.h>
#include <vespa/searchlib/fef/fieldtype.h>
#include <vespa/searchlib/fef/featurenamebuilder.h>
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/searchlib/fef/handle.h>

namespace search {
namespace features {

TermInfoBlueprint::TermInfoBlueprint()
    : search::fef::Blueprint("termInfo"),
      _termIdx(0)
{
}

void
TermInfoBlueprint::visitDumpFeatures(const search::fef::IIndexEnvironment &,
                                     search::fef::IDumpFeatureVisitor &) const
{
}

bool
TermInfoBlueprint::setup(const search::fef::IIndexEnvironment &,
                         const search::fef::ParameterList & params)
{
    _termIdx = params[0].asInteger();
    describeOutput("queryidx", "The index of the first term with the given "
                   "term index in the query term ordering. -1 if not found.");
    return true;
}

search::fef::FeatureExecutor &
TermInfoBlueprint::createExecutor(const search::fef::IQueryEnvironment &queryEnv, vespalib::Stash &stash) const
{
    feature_t queryIdx = -1.0;
    if (queryEnv.getNumTerms() > _termIdx) {
        queryIdx = _termIdx;
    }
    std::vector<feature_t> values;
    values.push_back(queryIdx);
    return stash.create<ValueExecutor>(values);
}

} // namespace features
} // namespace search
