// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bitword.h"

namespace search {

namespace {

template <typename T>
void fillUp(T * v, T startVal) {
    for (size_t i(0); i < (sizeof(T)*8); i++) {
        v[i] = startVal << i;
    }
}

}

BitWord::Init BitWord::_initializer;

BitWord::Init::Init()
{
    fillUp(BitWord::_checkTab, std::numeric_limits<BitWord::Word>::max());
}

BitWord::Word BitWord::_checkTab[BitWord::WordLen];

}
