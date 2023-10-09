// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \file defs.h
 *
 * \brief Common declarations for the storage API code.
 */
#pragma once

#include <cstdint>

namespace storage:: api {

using Timestamp = uint64_t;
using VisitorId = uint32_t;

constexpr Timestamp MAX_TIMESTAMP = (Timestamp)-1LL;

}
