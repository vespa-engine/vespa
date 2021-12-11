// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <stdint.h>

namespace search {
namespace grouping {

class GroupRef
{
public:
    GroupRef() noexcept : _ref(-1) { }
    GroupRef(uint32_t ref) : _ref(ref) { }
    uint32_t getRef() const { return _ref; }
    bool valid() const { return _ref != static_cast<uint32_t>(-1); }
    operator uint32_t () const { return getRef(); }
private:
    uint32_t _ref;
};

}
}
