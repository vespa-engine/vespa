// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vector>
#include <vespa/searchlib/bitcompression/compression.h>
#include <vespa/searchlib/util/comprfile.h>

namespace search
{

namespace fakedata
{

template <bool bigEndian>
class BitEncode64 : public bitcompression::EncodeContext64<bigEndian>
{
    search::ComprFileWriteContext _cbuf;

public:
    BitEncode64();

    ~BitEncode64();

    typedef bitcompression::EncodeContext64<bigEndian> EC;

    void
    writeComprBuffer()
    {
        _cbuf.writeComprBuffer(true);
    }

    void
    writeComprBufferIfNeeded()
    {
        if (this->_valI >= this->_valE)
            _cbuf.writeComprBuffer(false);
    }

    std::pair<uint64_t *, size_t>
    grabComprBuffer(void *&comprBufMalloc)
    {
        std::pair<void *, size_t> tres = _cbuf.grabComprBuffer(comprBufMalloc);
        return std::make_pair(static_cast<uint64_t *>(tres.first),
                              tres.second);
    }
};

extern template class BitEncode64<true>;

extern template class BitEncode64<false>;

typedef BitEncode64<true> BitEncode64BE;

typedef BitEncode64<false> BitEncode64LE;

} // namespace fakedata

} // namespace search

