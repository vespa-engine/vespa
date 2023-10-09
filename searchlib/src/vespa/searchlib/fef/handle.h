// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace search::fef {

using FeatureHandle = uint32_t;
using TermFieldHandle = uint32_t;

const uint32_t IllegalHandle = 0xffffffff;

}
