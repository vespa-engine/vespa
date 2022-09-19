// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/arrayref.h>
#include <cstdint>

template <typename FltType = float>
struct DocVectorAccess
{
    virtual vespalib::ConstArrayRef<FltType> get(uint32_t docid) const = 0;
    virtual ~DocVectorAccess() = default;
};
