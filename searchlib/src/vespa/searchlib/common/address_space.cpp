// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "address_space.h"
#include <ostream>
#include <assert.h>

namespace search {

AddressSpace::AddressSpace(size_t used_, size_t dead_, size_t limit_)
    : _used(used_),
      _dead(dead_),
      _limit(limit_)
{
    assert(_used >= _dead);
}

std::ostream &operator << (std::ostream &out, const AddressSpace &rhs)
{
    return out << "used=" << rhs.used() << ", dead=" << rhs.dead() << ", limit=" << rhs.limit();
}

} // namespace search

