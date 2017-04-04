// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/fastlib/io/filteroutputstream.h>

class Fast_HTTPChunkedOutputStream : public Fast_FilterOutputStream
{
  private:
    // Prevent use of:
    Fast_HTTPChunkedOutputStream(const Fast_HTTPChunkedOutputStream &);
    Fast_HTTPChunkedOutputStream & operator=(const Fast_HTTPChunkedOutputStream &);
  protected:
    size_t  _chunkSize;
    char   *_buffer;
    size_t  _bufferUsed;
    bool    _writeHasFailed;

    bool WriteChunk(void);
  public:
    Fast_HTTPChunkedOutputStream(Fast_OutputStream &out, size_t chunkSize = 1024);
    ~Fast_HTTPChunkedOutputStream();


    // Methods
    bool    Close() override;
    ssize_t Write(const void *sourceBuffer, size_t length) override;
    void    Flush() override;
};



