// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "address_space.h"
#include <ostream>
#include <cassert>

namespace vespalib {

AddressSpace::AddressSpace()
    : _used(0),
      _dead(0),
      _limit(0)
{
}

AddressSpace::AddressSpace(size_t used_, size_t dead_, size_t limit_)
    : _used(used_),
      _dead(dead_),
      _limit(limit_)
{
    assert(_used >= _dead);
}

std::ostream &operator << (std::ostream &out, const AddressSpace &rhs)
{
    return out << "{used=" << rhs.used() << ", dead=" << rhs.dead() << ", limit=" << rhs.limit() << "}";
}

} // namespace vespalib

