// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "outputstream.h"

class Fast_FilterOutputStream : public Fast_OutputStream
{
  private:

    // Prevent use of:
    Fast_FilterOutputStream();
    Fast_FilterOutputStream(Fast_FilterOutputStream &);
    Fast_FilterOutputStream &operator=(const Fast_FilterOutputStream &);

  protected:
    /** The stream to forward data to */
    Fast_OutputStream *_out;
  public:
    Fast_FilterOutputStream(Fast_OutputStream &out) : _out(&out) {}
    ~Fast_FilterOutputStream() {}

    bool Close()   override { return _out->Close(); }
    void Flush()   override {        _out->Flush(); }

    ssize_t Write(const void *sourceBuffer, size_t length) override {
      return _out->Write(sourceBuffer, length);
    }
};
