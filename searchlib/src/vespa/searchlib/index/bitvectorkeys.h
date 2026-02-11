// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace search::index {

struct BitVectorWordSingleKey {
    uint64_t _wordNum;
    uint32_t _numDocs;
    uint32_t _pad;

    BitVectorWordSingleKey() noexcept
        : _wordNum(0),
          _numDocs(0),
          _pad(0)
    {
    }

    bool
    operator<(const BitVectorWordSingleKey &rhs) const noexcept
    {
        return  _wordNum < rhs._wordNum;
    }

    bool
    operator==(const BitVectorWordSingleKey &rhs) const noexcept
    {
        return _wordNum == rhs._wordNum;
    }
};

}
