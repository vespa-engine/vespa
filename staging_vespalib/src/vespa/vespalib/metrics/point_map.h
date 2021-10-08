// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <map>
#include "dimension.h"
#include "label.h"

namespace vespalib {
namespace metrics {

using PointMap = std::map<Dimension, Label>;

} // namespace vespalib::metrics
} // namespace vespalib
