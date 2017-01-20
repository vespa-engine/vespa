// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "output.h"
#include "string.h"

namespace vbench {

/**
 * Concrete utility class used to write small amounts of data to an
 * output efficiently by buffering the output into larger chunks.
 **/
class BufferedOutput
{
private:
    Output        &_output;
    WritableMemory _data;
    size_t         _pos;
    size_t         _chunkSize;

    void ensureFree(size_t bytes) {
        if ((_pos + bytes) > _data.size) {
            _data = _output.commit(_pos).reserve(std::max(bytes, _chunkSize));
            _pos = 0;
        }
    }

public:
    BufferedOutput(Output &output, size_t chunkSize)
        : _output(output), _data(), _pos(), _chunkSize(chunkSize) {}
    ~BufferedOutput() { _output.commit(_pos); }

    BufferedOutput &append(char c) {
        ensureFree(1);
        _data.data[_pos++] = c;
        return *this;
    }

    BufferedOutput &append(const char *data, size_t size) {
        ensureFree(size);
        memcpy(_data.data + _pos, data, size);
        _pos += size;
        return *this;
    }

    BufferedOutput &append(const string &str) {
        return append(str.data(), str.size());
    }

    BufferedOutput &append(const char *str) {
        return append(str, strlen(str));
    }

    BufferedOutput &printf(const char *fmt, ...)
#ifdef __GNUC__
        // Add printf format checks with gcc
        __attribute__ ((format (printf,2,3)))
#endif
        ;
};

} // namespace vbench

