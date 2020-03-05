// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vector>
#include <memory>
#include <cstring>

namespace vespalib {

struct ReusableSet
{
    using Mark = unsigned short;

    Mark *bits;
    Mark curval;
    size_t sz;

    explicit ReusableSet(size_t size)
      : bits((Mark *)malloc(size * sizeof(Mark))),
        curval(-1),
        sz(size)
    {
        clear();
    }

    ~ReusableSet() {
        free(bits);
    }

    void clear() {
        if (++curval == 0) {
            memset(bits, 0, sz * sizeof(Mark));
            ++curval;
        }
    }

    void mark(size_t id) {
        bits[id] = curval;
    }

    bool isMarked(size_t id) const {
        return (bits[id] == curval);
    }
};

} // namespace
