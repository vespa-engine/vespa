// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".features.value");
#include "valuefeature.h"

#include <sstream>

namespace search {
namespace features {

ValueExecutor::ValueExecutor(const std::vector<feature_t> & values) :
    search::fef::FeatureExecutor(),
    _values(values)
{
    // empty
}

void
ValueExecutor::execute(search::fef::MatchData & data)
{
    for (uint32_t i = 0; i < _values.size(); ++i) {
        *data.resolveFeature(outputs()[i]) = _values[i];
    }
}

void
SingleZeroValueExecutor::execute(search::fef::MatchData & data)
{
    *data.resolveFeature(outputs()[0]) = 0.0;
}

ValueBlueprint::ValueBlueprint() :
    search::fef::Blueprint("value"),
    _values()
{
    // empty
}

void
ValueBlueprint::visitDumpFeatures(const search::fef::IIndexEnvironment &,
                                  search::fef::IDumpFeatureVisitor &) const
{
    // empty
}

bool
ValueBlueprint::setup(const search::fef::IIndexEnvironment &,
                      const search::fef::ParameterList & params)
{
    for (uint32_t i = 0; i < params.size(); ++i) {
        _values.push_back(params[i].asDouble());
        std::ostringstream name;
        name << i;
        std::ostringstream desc;
        desc << "value " << i;
        describeOutput(name.str(), desc.str());
        // we have no inputs
    }
    return true;
}

search::fef::FeatureExecutor &
ValueBlueprint::createExecutor(const search::fef::IQueryEnvironment &queryEnv, vespalib::Stash &stash) const
{
    (void) queryEnv;
    return stash.create<ValueExecutor>(_values);
}


} // namespace features
} // namespace search
