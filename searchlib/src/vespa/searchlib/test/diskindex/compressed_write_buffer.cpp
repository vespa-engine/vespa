// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "compressed_write_buffer.h"
#include <vespa/searchlib/bitcompression/compression.h>
#include <cassert>

namespace search::diskindex::test {

template <bool bigEndian>
CompressedWriteBuffer<bigEndian>::CompressedWriteBuffer(EC& e)
    : _e(e),
      _wc(e),
      _header_len(0u),
      _file_bit_size(0u)
{
    _wc.allocComprBuf();
    _e.setWriteContext(&_wc);
    clear();
}

template <bool bigEndian>
CompressedWriteBuffer<bigEndian>::~CompressedWriteBuffer() = default;

template <bool bigEndian>
void
CompressedWriteBuffer<bigEndian>::clear()
{
    _e.setupWrite(_wc);
    assert(_e.getWriteOffset() == 0);
    _header_len = 0;
    _file_bit_size = 0;
}

template <bool bigEndian>
void
CompressedWriteBuffer<bigEndian>::flush()
{
    _file_bit_size = _e.getWriteOffset();
    _e.padBits(128);
    _e.flush();
}

template <bool bigEndian>
void
CompressedWriteBuffer<bigEndian>::start_pad(uint32_t header_len)
{
    _e.padBits(header_len * 8);
    _header_len = header_len;
}

template class CompressedWriteBuffer<true>;
template class CompressedWriteBuffer<false>;

}
