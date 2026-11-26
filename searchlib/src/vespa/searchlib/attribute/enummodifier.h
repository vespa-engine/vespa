// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "enum_lock.h"

#include <mutex>

namespace search::attribute {

class InterlockGuard;

class EnumModifier {
    std::unique_lock<EnumLock> _enumLock;

public:
    EnumModifier(EnumLock& lock, InterlockGuard& interlockGuard);
    ~EnumModifier();
};

} // namespace search::attribute
