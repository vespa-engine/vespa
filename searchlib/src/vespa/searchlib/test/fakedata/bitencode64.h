// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vector>
#include <vespa/searchlib/bitcompression/compression.h>
#include <vespa/searchlib/util/comprfile.h>

namespace search::fakedata {

template <bool bigEndian>
class BitEncode64 : public bitcompression::EncodeContext64<bigEndian>
{
    search::ComprFileWriteContext _cbuf;

public:
    BitEncode64();
    ~BitEncode64() override;

    using EC = bitcompression::EncodeContext64<bigEndian>;

    void writeComprBuffer() {
        _cbuf.writeComprBuffer(true);
    }

    void writeComprBufferIfNeeded() {
        if (this->_valI >= this->_valE)
            _cbuf.writeComprBuffer(false);
    }

    std::pair<uint64_t *, size_t>
    grabComprBuffer(vespalib::alloc::Alloc & comprAlloc) {
        return _cbuf.grabComprBuffer(comprAlloc);
    }
};

extern template class BitEncode64<true>;
extern template class BitEncode64<false>;

}
