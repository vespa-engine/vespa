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

} // namespace fef
} // namespace search
