// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/fastlib/io/filterinputstream.h>

class Fast_BufferedInputStream : public Fast_FilterInputStream
{
protected:
    // Buffer attributes
    char          *_buffer;
    const size_t   _bufferSize;
    size_t         _bufferUsed;       // Amount of buffer currently holding data
    size_t         _bufferRead;       // How far buffer has been read
    bool           _nextWillFail;


public:
    Fast_BufferedInputStream(const Fast_BufferedInputStream &) = delete;
    Fast_BufferedInputStream & operator = (const Fast_BufferedInputStream &) = delete;

    // Constructor
    Fast_BufferedInputStream(Fast_InputStream &in, size_t bufferSize = 1024);

    // Destructor
    virtual ~Fast_BufferedInputStream();

    // Subclassed methods
    ssize_t  Available() override;
    bool     Close() override;
    ssize_t  Skip(size_t skipNBytes) override;
    ssize_t  Read(void *targetBuffer, size_t length) override;

    // Additional methods
    ssize_t  ReadBufferFullUntil(void *targetBuffer, size_t maxlength, char stopChar);
};
