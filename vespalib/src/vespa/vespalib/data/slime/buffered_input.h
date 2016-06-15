// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "memory.h"
#include <assert.h>

namespace vespalib {
namespace slime {

/**
 * A simple class tracking the consumption of input data. Also keeps
 * track of data underflow.
 **/
class BufferedInput
{
private:
    const char *_begin; // start of input
    const char *_pos;   // current read position
    const char *_end;   // end of input
    const char *_fail;  // read position at failure
    std::string _msg;   // error message of failure

public:
    explicit BufferedInput(const Memory &memory)
        : _begin(memory.data), _pos(_begin), _end(_pos + memory.size),
          _fail(0), _msg() {}

    bool eof() const { return (_pos == _end); }

    bool failed() const { return (_fail != 0); }

    const std::string &getErrorMessage() const { return _msg; }

    Memory getConsumed() const {
        if (_fail != 0) {
            return Memory();
        }
        return Memory(_begin, (_pos - _begin));
    }

    Memory getOffending() const {
        if (_fail == 0) {
            return Memory();
        }
        return Memory(_begin, (_fail - _begin));
    }

    void fail(const std::string &msg) {
        if (_fail == 0) {
            _fail = _pos;
            _msg = msg;
            _pos = _end;
        }
    }

    char getByte() {
        if (_pos == _end) {
            fail("input buffer underflow");
            return 0;
        }
        return *_pos++;
    }

    Memory getBytes(size_t n) {
        if ((_pos + n) > _end) {
            assert(_fail == 0 || _pos == _end);
            _pos = _end;
            fail("input buffer underflow");
            return Memory();
        }
        Memory ret(_pos, n);
        _pos += n;
        return ret;
    }
};

} // namespace vespalib::slime
} // namespace vespalib

