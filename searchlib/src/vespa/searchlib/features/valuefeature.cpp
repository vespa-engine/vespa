// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "valuefeature.h"
#include <vespa/vespalib/stllike/asciistream.h>

namespace search {
namespace features {

ValueExecutor::ValueExecutor(const std::vector<feature_t> & values) :
    search::fef::FeatureExecutor(),
    _values(values)
{
}

void
ValueExecutor::execute(uint32_t)
{
    for (uint32_t i = 0; i < _values.size(); ++i) {
        outputs().set_number(i, _values[i]);
    }
}

void
SingleZeroValueExecutor::execute(uint32_t)
{
    outputs().set_number(0, 0.0);
}

ValueBlueprint::ValueBlueprint() :
    search::fef::Blueprint("value"),
    _values()
{
}

ValueBlueprint::~ValueBlueprint() {}

void
ValueBlueprint::visitDumpFeatures(const search::fef::IIndexEnvironment &,
                                  search::fef::IDumpFeatureVisitor &) const
{
}

bool
ValueBlueprint::setup(const search::fef::IIndexEnvironment &,
                      const search::fef::ParameterList & params)
{
    for (uint32_t i = 0; i < params.size(); ++i) {
        _values.push_back(params[i].asDouble());
        vespalib::asciistream name;
        name << i;
        vespalib::asciistream desc;
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
