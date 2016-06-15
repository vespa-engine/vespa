// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    BitEncode64(void);

    ~BitEncode64(void);

    typedef bitcompression::EncodeContext64<bigEndian> EC;

    void
    writeComprBuffer(void)
    {
        _cbuf.writeComprBuffer(true);
    }

    void
    writeComprBufferIfNeeded(void)
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

