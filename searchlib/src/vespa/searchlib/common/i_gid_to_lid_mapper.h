// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <functional>

namespace document { class GlobalId; }

namespace search {

/*
 * Interface for gid to lid mapper visitor.
 */
class IGidToLidMapperVisitor
{
public:
    virtual ~IGidToLidMapperVisitor() = default;
    virtual void visit(const document::GlobalId &gid, uint32_t lid) const = 0;
};


/*
 * Interface class for mapping from gid to lid. Instances should be short
 * lived due to implementations containing read guards preventing resource
 * reuse.
 */
class IGidToLidMapper
{
public:
    virtual ~IGidToLidMapper() = default;
    virtual void foreach(const IGidToLidMapperVisitor &visitor) const = 0;
};

} // namespace search
