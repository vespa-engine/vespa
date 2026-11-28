// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "shared_enum_mutex.h"

#include <mutex>

namespace search::attribute {

class InterlockGuard;

class EnumModifier {
    std::unique_lock<SharedEnumMutex> _enumMutex;

public:
    EnumModifier(SharedEnumMutex& lock, InterlockGuard& interlockGuard);
    ~EnumModifier();
};

} // namespace search::attribute
