// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "filealign.h"
#include <vespa/vespalib/util/alloc.h>

namespace search {

class ComprBuffer
{
private:
    using Alloc = vespalib::alloc::Alloc;
    void allocComprBuf();
    const uint32_t   _unitSize; // Size of unit in bytes, doubles up as alignment
    bool             _padBefore;
    void            *_comprBuf;
    size_t           _comprBufSize;
    Alloc            _comprAlloc;
    FileAlign        _aligner;
public:

    ComprBuffer(const ComprBuffer &) = delete;
    ComprBuffer &operator=(const ComprBuffer &) = delete;
    ComprBuffer(uint32_t unitSize);
    virtual ~ComprBuffer();

    void dropComprBuf();

    void allocComprBuf(size_t comprBufSize, size_t preferredFileAlignment,
                       FastOS_FileInterface *const file, bool padbefore);

    static size_t minimumPadding() { return 8; }
    uint32_t getUnitBitSize() const { return _unitSize * 8; }
    uint32_t getUnitSize() const { return _unitSize; }
    const uint64_t * getComprBuf() const { return static_cast<const uint64_t *>(_comprBuf); }
    uint64_t * getComprBuf() { return static_cast<uint64_t *>(_comprBuf); }
    size_t getComprBufSize() const { return _comprBufSize; }
    void setComprBuf(void * buf, size_t sz) {
        _comprBuf = buf;
        _comprBufSize = sz;
    }

    const uint64_t * getAdjustedBuf(size_t offset) const {
        return getComprBuf() + _aligner.adjustElements(offset / sizeof(uint64_t),getComprBufSize());
    }
    const FileAlign & getAligner() const { return _aligner; }

    Alloc stealComprBuf() {
        setComprBuf(nullptr, 0);
        return std::move(_comprAlloc);
    }

    /*
     * When encoding to memory instead of file, the compressed buffer must
     * be able to grow.
     */
    void expandComprBuf(uint32_t overflowUnits);

    /*
     * For unit testing only. Reference data owned by rhs, only works as
     * long as rhs is live and unchanged.
     */
    void referenceComprBuf(const ComprBuffer &rhs);
};

}

