// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "memory.h"
#include "writable_memory.h"

namespace vespalib {

struct Output;

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
    ~OutputWriter();

    /**
     * Reserve the requested number of bytes in the output and return
     * a pointer to the first one. You must call the commit function
     * after writing the bytes to make them part of the output. All
     * other writer operations will invalidate the pointer returned
     * from this function.
     *
     * @param the number of bytes to reserve
     * @return pointer to reserved bytes
     **/
    char *reserve(size_t bytes) {
        if (__builtin_expect((_pos + bytes) <= _data.size, true)) {
            return (_data.data + _pos);
        }
        return reserve_slow(bytes);
    }

    /**
     * Commit bytes written to a memory region previously returned by
     * the reserve function. You must never commit more bytes than you
     * reserved. You should never commit bytes that are not written
     * (their values will be undefined).
     *
     * @param bytes the number of bytes to commit
     **/
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

    void write(const Memory &memory) {
        memcpy(reserve(memory.size), memory.data, memory.size);
        commit(memory.size);
    }

    void printf(const char *fmt, ...) __attribute__ ((format (printf,2,3)));
};

} // namespace vespalib
