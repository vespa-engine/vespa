// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "inputstream.h"
#include <vespa/fastos/file.h>

class Fast_FileInputStream  : public Fast_InputStream
{
private:
    Fast_FileInputStream(const Fast_FileInputStream&);
    Fast_FileInputStream& operator=(const Fast_FileInputStream&);

protected:

    /** Pointer to the physical file object*/
    FastOS_FileInterface  *_theFile;

    /** File opened ok flag */
    bool  _fileOpenedOk;

public:
    Fast_FileInputStream(const char *fileName);
    ~Fast_FileInputStream();

    // Implementation of Fast_InputStream interface

    ssize_t Read(void *targetBuffer, size_t bufferSize) override;
    bool Close() override;
    ssize_t Available() override;
    ssize_t Skip(size_t) override;
};
