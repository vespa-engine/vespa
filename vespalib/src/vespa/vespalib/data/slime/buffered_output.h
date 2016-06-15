// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "output.h"
#include <string.h>
#include <algorithm>

namespace vespalib {
namespace slime {

/**
 * Thin layer on top of the Output interface that supplies us with an
 * inlined API for efficient buffer handling.
 **/
class BufferedOutput
{
private:
    enum { CHUNK_SIZE = 8000 };

    Output &_output;
    char   *_start;
    char   *_pos;
    char   *_end;

public:
    BufferedOutput(Output &out)
        : _output(out), _start(0), _pos(0), _end(0) {}

    char *reserve(size_t bytes) {
        if (__builtin_expect((size_t)(_end - _pos) < bytes, false)) {
            size_t wantBytes = std::max(size_t(CHUNK_SIZE), bytes);
            _start = _output.exchange(_start, _pos - _start, wantBytes);
            _pos = _start;
            _end = _start + wantBytes;
        }
        return _pos;
    }

    void commit(size_t bytes) {
        _pos += bytes;
    }

    void writeByte(char value) {
        reserve(1)[0] = value;
        commit(1);
    }

    void writeBytes(const char *data, size_t size) {
        char *p = reserve(size);
        memcpy(p, data, size);
        commit(size);
    }

    void printf(const char *fmt, ...)
#ifdef __GNUC__
        // Add printf format checks with gcc
        __attribute__ ((format (printf,2,3)))
#endif
        ;

    ~BufferedOutput() {
        if (_pos != _start) {
            _output.exchange(_start, _pos - _start, 0);
        }
    }
};

} // namespace vespalib::slime
} // namespace vespalib

