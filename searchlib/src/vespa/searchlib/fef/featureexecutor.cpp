// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
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
FeatureExecutor::handle_bind_match_data(MatchData &)
{
}

void
FeatureExecutor::bind_match_data(MatchData &md)
{
    _inputs.bind(md);
    _outputs.bind(md);
    handle_bind_match_data(md);
}

} // namespace fef
} // namespace search
