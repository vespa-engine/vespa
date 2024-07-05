// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "valuefeature.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/stash.h>

using namespace search::fef;
namespace search::features {

ValueExecutor::ValueExecutor(const std::vector<feature_t> & values) :
    FeatureExecutor(),
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
SingleValueExecutor::execute(uint32_t)
{
    outputs().set_number(0, _value);
}

void
SingleZeroValueExecutor::execute(uint32_t)
{
    outputs().set_number(0, 0.0);
}

ValueBlueprint::ValueBlueprint() :
    Blueprint("value"),
    _values()
{
}

ValueBlueprint::~ValueBlueprint() = default;

void
ValueBlueprint::visitDumpFeatures(const IIndexEnvironment &, IDumpFeatureVisitor &) const
{
}

bool
ValueBlueprint::setup(const IIndexEnvironment &, const ParameterList & params)
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

FeatureExecutor &
ValueBlueprint::createExecutor(const IQueryEnvironment &, vespalib::Stash &stash) const
{
    if (_values.size() == 1) {
        return stash.create<SingleValueExecutor>(_values[0]);
    } else {
        return stash.create<ValueExecutor>(_values);
    }
}


}
