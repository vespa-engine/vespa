// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <span>

template <typename FltType = float>
struct DocVectorAccess
{
    virtual std::span<const FltType> get(uint32_t docid) const = 0;
    virtual ~DocVectorAccess() = default;
};
