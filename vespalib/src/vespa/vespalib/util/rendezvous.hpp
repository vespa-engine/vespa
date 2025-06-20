// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "exceptions.h"
#include <algorithm>
#include <cassert>
#include <limits>

namespace vespalib {

namespace {

void throw_illegal_state() {
    throw(IllegalStateException("trying to use destroyed rendezvous"));
}

bool is_bad_gen(size_t destroyed_at, size_t my_gen) {
    // handle generation wrap-around
    return (my_gen - destroyed_at) < (std::numeric_limits<size_t>::max() / 2);
}

} // unnamed

template <typename IN, typename OUT, bool external_id>
void
Rendezvous<IN, OUT, external_id>::check_destroyed(size_t my_gen, const std::unique_lock<std::mutex> &) const {
    if (_destroyed.load(std::memory_order_relaxed) && is_bad_gen(_destroyed_at, my_gen)) {
        throw_illegal_state();
    }
}

template <typename IN, typename OUT, bool external_id>
void
Rendezvous<IN, OUT, external_id>::meet_self(IN &input, OUT &output) {
    if (_destroyed.load(std::memory_order_relaxed)) {
        throw_illegal_state();
    }
    _in[0] = &input;
    _out[0] = &output;
    mingle();
}

template <typename IN, typename OUT, bool external_id>
void
Rendezvous<IN, OUT, external_id>::meet_others(IN &input, OUT &output, size_t my_id, std::unique_lock<std::mutex> guard)
{
    size_t my_gen = _gen;
    check_destroyed(my_gen, guard);
    if (external_id) {
        assert(_in[my_id] == nullptr);
        assert(_out[my_id] == nullptr);
    }
    _in[my_id] = &input;
    _out[my_id] = &output;
    if (++_next == _size) {
        mingle();
        if (external_id) {
            std::fill(_in.begin(), _in.end(), nullptr);
            std::fill(_out.begin(), _out.end(), nullptr);
        }
        _next = 0;
        ++_gen;
        _cond.notify_all();
    } else {
        while (my_gen == _gen) {
            _cond.wait(guard);
        }
        check_destroyed(my_gen, guard);
    }
}

template <typename IN, typename OUT, bool external_id>
Rendezvous<IN, OUT, external_id>::Rendezvous(size_t n)
    : _lock(),
      _cond(),
      _size(n),
      _next(0),
      _gen(0),
      _in(n, nullptr),
      _out(n, nullptr),
      _destroyed(false),
      _destroyed_at(0)
{
    if (n == 0) {
        throw IllegalArgumentException("size must be greater than 0");
    }
}

template <typename IN, typename OUT, bool external_id>
Rendezvous<IN, OUT, external_id>::~Rendezvous() = default;

template <typename IN, typename OUT, bool external_id>
void
Rendezvous<IN, OUT, external_id>::destroy() {
    std::unique_lock guard(_lock);
    if (!_destroyed.load(std::memory_order_relaxed)) {
        _destroyed.store(true, std::memory_order_relaxed);
        _destroyed_at = _gen++;
        _cond.notify_all();
    }
}

template <typename IN, typename OUT, bool external_id>
OUT
Rendezvous<IN, OUT, external_id>::rendezvous(IN input)
    requires (!external_id)
{
    OUT ret{};
    static_assert(!external_id);
    if (_size == 1) {
        meet_self(input, ret);
    } else {
        std::unique_lock guard(_lock);
        meet_others(input, ret, _next, std::move(guard));
    }
    return ret;
}

template <typename IN, typename OUT, bool external_id>
OUT
Rendezvous<IN, OUT, external_id>::rendezvous(IN input, size_t my_id)
    requires (external_id)
{
    OUT ret{};
    assert(my_id < _size);
    static_assert(external_id);
    if (_size == 1) {
        meet_self(input, ret);
    } else {
        meet_others(input, ret, my_id, std::unique_lock(_lock));
    }
    return ret;
}

} // namespace vespalib
