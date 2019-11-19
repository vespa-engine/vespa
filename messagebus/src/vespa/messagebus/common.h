// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <chrono>

namespace mbus {

// Decide the type of string used once
using string = vespalib::string;

using seconds = std::chrono::duration<double>;


} // namespace mbus

