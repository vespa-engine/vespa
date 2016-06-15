// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 1999-2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

#pragma once

#include <vespa/fastos/fastos.h>
#include <vespa/searchlib/util/filealign.h>

namespace search {

class ComprBuffer
{
private:
    ComprBuffer(const ComprBuffer &);

    ComprBuffer &
    operator=(const ComprBuffer &);

    void
    allocComprBuf(void);
public:
    void *_comprBuf;
    size_t _comprBufSize;
    uint32_t _unitSize;	// Size of unit in bytes, doubles up as alignment
    bool _padBefore;
    void *_comprBufMalloc;
    FileAlign _aligner;

    ComprBuffer(uint32_t unitSize);

    virtual
    ~ComprBuffer(void);

    void
    dropComprBuf(void);

    void
    allocComprBuf(size_t comprBufSize,
                  size_t preferredFileAlignment,
                  FastOS_FileInterface *const file,
                  bool padbefore);

    static size_t
    minimumPadding(void)
    {
        return 8;
    }

    uint32_t
    getUnitBitSize(void) const
    {
        return _unitSize * 8;
    }

    bool
    getPadBefore(void) const
    {
        return _padBefore;
    }

    bool
    getCheckPointResumed(void) const
    {
        return _aligner.getCheckPointResumed();
    }

    /*
     * When encoding to memory instead of file, the compressed buffer must
     * be able to grow.
     */
    void
    expandComprBuf(uint32_t overflowUnits);

    /*
     * For unit testing only. Reference data owned by rhs, only works as
     * long as rhs is live and unchanged.
     */
    void
    referenceComprBuf(const ComprBuffer &rhs);

    /**
     * Checkpoint write.  Used at semi-regular intervals during indexing
     * to allow for continued indexing after an interrupt.
     */
    void
    checkPointWrite(vespalib::nbostream &out);

    /**
     * Checkpoint read.  Used when resuming indexing after an interrupt.
     *
     */
    void
    checkPointRead(vespalib::nbostream &in);
};

}

