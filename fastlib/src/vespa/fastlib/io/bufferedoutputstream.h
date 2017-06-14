// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/fastlib/io/filteroutputstream.h>

class Fast_BufferedOutputStream : public Fast_FilterOutputStream
{
private:

    // Prevent use of:
    Fast_BufferedOutputStream(const Fast_BufferedOutputStream &);
    Fast_BufferedOutputStream & operator=(const Fast_BufferedOutputStream &);


protected:

    // Buffer attributes
    char    *_buffer;
    size_t   _bufferSize;
    size_t   _bufferUsed;       // Amount of buffer currently holding data
    size_t   _bufferWritten;    // How far buffer has been written
    bool     _nextWillFail;


public:

    // Constructor
    Fast_BufferedOutputStream(Fast_OutputStream &out, size_t bufferSize = 1024);

    // Destructor
    ~Fast_BufferedOutputStream();

    // Methods
    bool     Close() override;
    ssize_t  Write(const void *sourceBuffer, size_t length) override;
    void     Flush() override;
};
