// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "input.h"

namespace vbench {

/**
 * Concrete utility class used to read input data one byte at a time
 * by wrapping a generic Input implementation.
 **/
class ByteInput
{
private:
    Input  &_input;
    Memory  _data;
    size_t  _pos;
    size_t  _chunkSize;

public:
    /**
     * Wrap an Input to read one byte at a time.
     *
     * @param input the underlying Input
     * @param chunkSize how much data to request from the input per transaction
     **/
    ByteInput(Input &input, size_t chunkSize)
        : _input(input), _data(), _pos(0), _chunkSize(chunkSize) {}
    ~ByteInput() { _input.evict(_pos); }

    /**
     * Read the next byte of input.
     *
     * @return next byte of input, or -1 if no more input is available
     **/
    int get() {
        if (_pos < _data.size) {
            return (_data.data[_pos++] & 0xff);
        } else {
            _data = _input.evict(_pos).obtain(_chunkSize, 1);
            if ((_pos = 0) < _data.size) {
                return (_data.data[_pos++] & 0xff);
            }
        }
        return -1;
    }
};

} // namespace vbench

