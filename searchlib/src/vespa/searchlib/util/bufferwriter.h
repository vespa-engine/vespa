// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>

namespace search {

/**
 * Abstract class to write to a buffer with an abstract backing store
 * and abstract backing buffer.  Each time backing buffer is full,
 * flush() is called to resize it or drain it to the backing store.
 */
class BufferWriter
{
    char *_cur;
    char *_end;
    char *_start;
protected:
    void rewind() { _cur = _start; }

    void setup(void *start, size_t len) {
        _start = static_cast<char *>(start);
        _end = _start + len;
        rewind();
    }

    size_t freeLen() const { return _end - _cur; }
    size_t usedLen() const { return _cur - _start; }

    void writeFast(const void *src, size_t len)
    {
        __builtin_memcpy(_cur, src, len);
        _cur += len;
    }

    void writeSlow(const void *src, size_t len);

public:
    BufferWriter();

    virtual ~BufferWriter();

    virtual void flush() = 0;

    void write(const void *src, size_t len)
    {
        if (__builtin_expect(len <= freeLen(), true)) {
            writeFast(src, len);
            return;
        }
        writeSlow(src, len);
    }
};

} // namespace search
