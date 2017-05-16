// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <sys/types.h>

class Fast_InputStream
{
public:
    virtual ~Fast_InputStream() { }

    virtual ssize_t Available() = 0;
    virtual bool    Close() = 0;
    virtual ssize_t Read(void *targetBuffer, size_t bufferSize) = 0;
    virtual ssize_t Skip(size_t skipNBytes) = 0;
};
