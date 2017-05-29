// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>

class Fast_BufferedInputStream;

class Fast_HTTPHeaderParser
{
public:
    Fast_HTTPHeaderParser(const Fast_HTTPHeaderParser &) = delete;
    Fast_HTTPHeaderParser & operator = (const Fast_HTTPHeaderParser &) = delete;
    Fast_HTTPHeaderParser(Fast_BufferedInputStream &in);
    ~Fast_HTTPHeaderParser();

    // Methods
    bool ReadRequestLine(const char *&method, const char *&url, int &versionMajor, int &versionMinor);
    bool ReadHeader(const char *&name, const char *&value);
private:
    char                      _pushBack;
    bool                      _isPushBacked;
    const size_t              _bufferSize;
    char                     *_lineBuffer;
    Fast_BufferedInputStream *_input;
};
