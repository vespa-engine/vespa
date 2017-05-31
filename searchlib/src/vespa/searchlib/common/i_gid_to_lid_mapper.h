// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace document { class GlobalId; }

namespace search {

/*
 * Interface class for mapping from gid to lid. Instances should be short
 * lived due to implementations containing read guards preventing resource
 * reuse.
 */
class IGidToLidMapper
{
public:
    virtual ~IGidToLidMapper() { }
    virtual uint32_t mapGidToLid(const document::GlobalId &gid) const = 0;
};

} // namespace search
