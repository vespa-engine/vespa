// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "output.h"
#include <string.h>
#include <algorithm>

namespace vespalib {

/**
 * Thin layer on top of the Output interface that supplies us with an
 * inlined API for efficient buffer handling.
 **/
class OutputWriter
{
private:
    Output        &_output;
    WritableMemory _data;
    size_t         _pos;
    size_t         _chunk_size;

    char *reserve_slow(size_t bytes);

public:
    OutputWriter(Output &output, size_t chunk_size)
        : _output(output), _data(), _pos(0), _chunk_size(chunk_size) {}
    ~OutputWriter() { _output.commit(_pos); }

    char *reserve(size_t bytes) {
        if (__builtin_expect((_pos + bytes) <= _data.size, true)) {
            return (_data.data + _pos);
        }
        return reserve_slow(bytes);
    }

    void commit(size_t bytes) {
        _pos += bytes;
    }

    void write(char value) {
        reserve(1)[0] = value;
        commit(1);
    }

    void write(const char *data, size_t size) {
        memcpy(reserve(size), data, size);
        commit(size);
    }

    void printf(const char *fmt, ...)
#ifdef __GNUC__
        // Add printf format checks with gcc
        __attribute__ ((format (printf,2,3)))
#endif
        ;
};

} // namespace vespalib
