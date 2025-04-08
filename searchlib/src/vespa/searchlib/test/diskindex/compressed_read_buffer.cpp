// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "compressed_read_buffer.h"
#include "compressed_write_buffer.h"
#include <vespa/searchlib/bitcompression/compression.h>

namespace search::diskindex::test {

template <bool bigEndian>
CompressedReadBuffer<bigEndian>::CompressedReadBuffer(DC& d, const CompressedWriteBuffer<bigEndian>& wb)
    : _d(d),
      _rc(d),
      _header_len(0),
      _file_bit_size(0)
{
    _d.setReadContext(&_rc);
    rewind(wb);
}

template <bool bigEndian>
CompressedReadBuffer<bigEndian>::~CompressedReadBuffer() = default;

template <bool bigEndian>
void
CompressedReadBuffer<bigEndian>::rewind(const CompressedWriteBuffer<bigEndian>& wb)
{
    _rc.referenceWriteContext(wb.get_write_context());
    _header_len = wb.get_header_len();
    _file_bit_size = wb.get_file_bit_size();
    _d.skipBits(_header_len * 8);
}

template class CompressedReadBuffer<true>;
template class CompressedReadBuffer<false>;

}
