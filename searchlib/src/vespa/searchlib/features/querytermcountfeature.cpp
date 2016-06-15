// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".features.querytermcountfeature");

#include <vespa/searchlib/fef/properties.h>
#include <vespa/searchlib/fef/fieldinfo.h>
#include <vespa/searchlib/fef/fieldtype.h>
#include <vespa/searchlib/fef/featurenamebuilder.h>
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/searchlib/fef/handle.h>
#include "querytermcountfeature.h"
#include "valuefeature.h"

using namespace search::fef;

namespace search {
namespace features {

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
    return Blueprint::UP(new QueryTermCountBlueprint());
}

bool
QueryTermCountBlueprint::setup(const IIndexEnvironment &,
                               const ParameterList &)
{
    describeOutput("out", "The number of query terms found in the query environment.");
    return true;
}

FeatureExecutor::LP
QueryTermCountBlueprint::createExecutor(const IQueryEnvironment & env) const
{
    std::vector<feature_t> values;
    values.push_back(static_cast<feature_t>(env.getNumTerms()));
    return FeatureExecutor::LP(new ValueExecutor(values));
}


} // namespace features
} // namespace search
