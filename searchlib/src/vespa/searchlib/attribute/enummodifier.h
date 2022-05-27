// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <shared_mutex>
#include <mutex>

namespace search::attribute {

class InterlockGuard;

class EnumModifier
{
    std::unique_lock<std::shared_mutex> _enumLock;
public:
    EnumModifier(std::shared_mutex &lock, InterlockGuard &interlockGuard);
    ~EnumModifier();
};

}
