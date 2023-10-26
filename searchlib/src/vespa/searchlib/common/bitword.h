// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <cstddef>
#include <limits>

namespace search {

class BitWord {
public:
    using Word = uint64_t;
    using Index = uint32_t;
    static Word checkTab(Index index) noexcept { return _checkTab[bitNum(index)]; }
    static constexpr Word startBits(Index index) noexcept { return (std::numeric_limits<Word>::max() >> 1) >> (WordLen - 1 - bitNum(index)); }
    static constexpr size_t WordLen = sizeof(Word)*8;
    static constexpr uint8_t bitNum(Index idx) noexcept { return (idx % WordLen); }
    static constexpr Word endBits(Index index) noexcept { return (std::numeric_limits<Word>::max() - 1) << bitNum(index); }
    static constexpr Word allBits() noexcept { return std::numeric_limits<Word>::max(); }
    static constexpr Index wordNum(Index idx) noexcept { return idx >> numWordBits(); }
    static constexpr Word mask(Index idx) noexcept { return Word(1) << bitNum(idx); }
    static constexpr uint8_t size_bits(uint8_t n) noexcept { return (n > 1) ? (1 + size_bits(n >> 1)) : 0; }
    static constexpr uint8_t numWordBits() noexcept { return size_bits(WordLen); }
private:

    static Word _checkTab[WordLen];
    struct Init {
        Init();
    };
    static Init _initializer;
};

}
