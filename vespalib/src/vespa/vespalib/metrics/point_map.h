// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "dimension.h"
#include "label.h"

#include <map>

namespace vespalib {
namespace metrics {

using PointMap = std::map<Dimension, Label>;

} // namespace metrics
} // namespace vespalib
