// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/fastlib/io/filterinputstream.h>

class Fast_HTTPChunkedInputStream : public Fast_FilterInputStream
{
  private:
    // Prevent use of:
    Fast_HTTPChunkedInputStream(const Fast_HTTPChunkedInputStream &);
    Fast_HTTPChunkedInputStream & operator=(const Fast_HTTPChunkedInputStream &);
  protected:
    size_t _chunkSize;
    bool   _inChunk;
    bool   _isClosed;

    bool ReadChunkHeader(void);
  public:
    Fast_HTTPChunkedInputStream(Fast_InputStream &in);
    ~Fast_HTTPChunkedInputStream();


    // Methods
    ssize_t Available() override;
    bool    Close() override;
    ssize_t Read(void *targetBuffer, size_t length) override;
    ssize_t Skip(size_t skipNBytes) override;
};



