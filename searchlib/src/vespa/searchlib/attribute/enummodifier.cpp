// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "enummodifier.h"

namespace search::attribute {

EnumModifier::EnumModifier(SharedEnumMutex& lock, attribute::InterlockGuard& interlockGuard) : _enumMutex(lock) {
    (void)interlockGuard;
}

EnumModifier::~EnumModifier() = default;

} // namespace search::attribute
