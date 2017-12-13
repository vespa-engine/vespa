// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include "handle.h"

namespace vespalib::metrics {

using DimensionName = vespalib::string;

struct DimensionTag {};

/**
 * Opaque handle representing an uniquely named dimension.
 **/
using Dimension = Handle<DimensionTag>;

} // namespace vespalib::metrics
