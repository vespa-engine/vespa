// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "querytermcountfeature.h"
#include "valuefeature.h"
#include <vespa/searchlib/fef/properties.h>
#include <vespa/searchlib/fef/fieldinfo.h>
#include <vespa/searchlib/fef/featurenamebuilder.h>
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/vespalib/util/stash.h>


using namespace search::fef;

namespace search::features {

QueryTermCountBlueprint::QueryTermCountBlueprint() :
    Blueprint("queryTermCount")
{
}

void
QueryTermCountBlueprint::visitDumpFeatures(const IIndexEnvironment & env,
                                           IDumpFeatureVisitor & visitor) const
{
    (void) env;
    visitor.visitDumpFeature(getBaseName());
}

Blueprint::UP
QueryTermCountBlueprint::createInstance() const
{
    return std::make_unique<QueryTermCountBlueprint>();
}

bool
QueryTermCountBlueprint::setup(const IIndexEnvironment &,
                               const ParameterList &)
{
    describeOutput("out", "The number of query terms found in the query environment.");
    return true;
}

FeatureExecutor &
QueryTermCountBlueprint::createExecutor(const IQueryEnvironment &env, vespalib::Stash &stash) const
{
    std::vector<feature_t> values;
    values.push_back(static_cast<feature_t>(env.getNumTerms()));
    return stash.create<ValueExecutor>(values);
}

}
