// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "outputstream.h"

class FastOS_FileInterface;

class Fast_FileOutputStream  : public Fast_OutputStream
{
private:
    Fast_FileOutputStream(const Fast_FileOutputStream&);
    Fast_FileOutputStream& operator=(const Fast_FileOutputStream&);

protected:

    /** Pointer to the physical file object*/
    FastOS_FileInterface  *_theFile;

public:
    Fast_FileOutputStream(const char *fileName);
    ~Fast_FileOutputStream();

    ssize_t Write(const void *sourceBuffer, size_t bufferSize) override;

    bool Close() override;
    void Flush() override {}
};
