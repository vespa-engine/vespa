// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "enummodifier.h"

namespace search::attribute {

EnumModifier::EnumModifier(std::shared_mutex &lock, attribute::InterlockGuard &interlockGuard)
    : _enumLock(lock)
{
    (void) interlockGuard;
}

EnumModifier::~EnumModifier() = default;

}
