// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "exceptions.h"

namespace vespalib {

template <typename IN, typename OUT>
Rendezvous<IN, OUT>::Rendezvous(size_t n)
    : _lock(),
      _cond(),
      _size(n),
      _next(0),
      _gen(0),
      _in(n, 0),
      _out(n, 0)
{
    if (n == 0) {
        throw IllegalArgumentException("size must be greater than 0");
    }
}

template <typename IN, typename OUT>
Rendezvous<IN, OUT>::~Rendezvous() = default;

template <typename IN, typename OUT>
OUT
Rendezvous<IN, OUT>::rendezvous(IN input)
{
    OUT ret = OUT();
    if (_size == 1) {
        _in[0] = &input;
        _out[0] = &ret;
        mingle();
    } else {
        std::unique_lock guard(_lock);
        size_t me = _next++;
        _in[me] = &input;
        _out[me] = &ret;
        if (_next == _size) {
            mingle();
            _next = 0;
            ++_gen;
            _cond.notify_all();
        } else {
            size_t oldgen = _gen;
            while (oldgen == _gen) {
                _cond.wait(guard);
            }
        }
    }
    return ret;
}

} // namespace vespalib
