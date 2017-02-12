// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "featureexecutor.h"

namespace search {
namespace fef {

FeatureExecutor::FeatureExecutor()
    : _inputs(),
      _outputs()
{
}

bool
FeatureExecutor::isPure()
{
    return false;
}

void
FeatureExecutor::handle_bind_inputs(vespalib::ConstArrayRef<const NumberOrObject *>)
{
}

void
FeatureExecutor::handle_bind_outputs(vespalib::ArrayRef<NumberOrObject>)
{
}

void
FeatureExecutor::handle_bind_match_data(MatchData &)
{
}

void
FeatureExecutor::bind_inputs(vespalib::ConstArrayRef<const NumberOrObject *> inputs)
{
    _inputs.bind(inputs);
    handle_bind_inputs(inputs);
}

void
FeatureExecutor::bind_outputs(vespalib::ArrayRef<NumberOrObject> outputs)
{
    _outputs.bind(outputs);
    handle_bind_outputs(outputs);
}

void
FeatureExecutor::bind_match_data(MatchData &md)
{
    handle_bind_match_data(md);
}

} // namespace fef
} // namespace search
