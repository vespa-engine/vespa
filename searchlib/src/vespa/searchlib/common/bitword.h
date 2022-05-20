// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <cstddef>
#include <limits>

namespace search {

class BitWord {
public:
    typedef uint64_t Word;
    typedef uint32_t Index;
    static Word checkTab(Index index) { return _checkTab[bitNum(index)]; }
    static Word startBits(Index index) { return (std::numeric_limits<Word>::max() >> 1) >> (WordLen - 1 - bitNum(index)); }
    static constexpr size_t WordLen = sizeof(Word)*8;
    static uint8_t bitNum(Index idx) { return (idx % WordLen); }
    static Word endBits(Index index) { return (std::numeric_limits<Word>::max() - 1) << bitNum(index); }
    static Word allBits() { return std::numeric_limits<Word>::max(); }
    static Index wordNum(Index idx) { return idx >> numWordBits(); }
    static Word mask(Index idx) { return Word(1) << bitNum(idx); }
    static constexpr uint8_t size_bits(uint8_t n) { return (n > 1) ? (1 + size_bits(n >> 1)) : 0; }
    static uint8_t numWordBits() { return size_bits(WordLen); }
private:

    static Word _checkTab[WordLen];
    struct Init {
        Init();
    };
    static Init _initializer;
};

}
