// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bitencode64.h"

namespace search::fakedata {

template <bool bigEndian>
BitEncode64<bigEndian>::BitEncode64()
    : bitcompression::EncodeContext64<bigEndian>(),
      _cbuf(*this)
{
    _cbuf.allocComprBuf(64, 1);
    this->afterWrite(_cbuf, 0, 0);
}

template <bool bigEndian>
BitEncode64<bigEndian>::~BitEncode64() = default;

template class BitEncode64<true>;
template class BitEncode64<false>;

}
