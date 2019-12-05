// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/time.h>

namespace mbus {

// Decide the type of string used once
using string = vespalib::string;
using duration = vespalib::duration;
using time_point = vespalib::steady_clock::time_point;

} // namespace mbus

