// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "outputstream.h"

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

    ssize_t Write(const void *sourceBuffer, size_t bufferSize) override {
      return _theFile->CheckedWrite(sourceBuffer, bufferSize) ?
          static_cast<ssize_t>(bufferSize) :
          static_cast<ssize_t>(-1);
    };

    bool Close() override { return _theFile->Close(); }
    void Flush() override {}
};
