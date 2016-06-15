// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "sumexecutor.h"
#include "matchdata.h"

namespace search {
namespace fef {

void
SumExecutor::execute(MatchData &data)
{
    feature_t sum = 0.0;
    for (uint32_t i = 0; i < inputs().size(); ++i) {
        sum += *data.resolveFeature(inputs()[i]);
    }
    *data.resolveFeature(outputs()[0]) = sum;
}

} // namespace fef
} // namespace search
