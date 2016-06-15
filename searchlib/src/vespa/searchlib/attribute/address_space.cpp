// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "address_space.h"
#include <iostream>

namespace search {

AddressSpace::AddressSpace(size_t used_, size_t limit_)
    : _used(used_),
      _limit(limit_)
{
}

std::ostream &operator << (std::ostream &out, const AddressSpace &rhs)
{
    return out << "used=" << rhs.used() << ", limit=" << rhs.limit();
}

} // namespace search

