// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include "handle.h"

namespace vespalib::metrics {

using LabelValue = vespalib::string;

struct LabelTag {};

/**
 * Opaque handle representing an uniquely named label.
 **/
using Label = Handle<LabelTag>;

} // namespace vespalib::metrics
