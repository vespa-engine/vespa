// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "generation_hold_list.h"
#include <cassert>

namespace vespalib {

template <typename T, bool track_bytes_held, bool use_deque>
void
GenerationHoldList<T, track_bytes_held, use_deque>::assign_generation_internal(generation_t current_gen)
{
    for (auto& elem : _phase_1_list) {
        _phase_2_list.push_back(ElemWithGen(std::move(elem), current_gen));
    }
    _phase_1_list.clear();
}

template <typename T, bool track_bytes_held, bool use_deque>
template <typename Func>
void
GenerationHoldList<T, track_bytes_held, use_deque>::reclaim_internal(generation_t oldest_used_gen, Func func)
{
    auto itr = _phase_2_list.begin();
    auto ite = _phase_2_list.end();
    for (; itr != ite; ++itr) {
        if (itr->gen >= oldest_used_gen) {
            break;
        }
        const auto& elem = itr->elem;
        func(elem);
        if constexpr (track_bytes_held) {
            _held_bytes.store(get_held_bytes() - itr->byte_size(), std::memory_order_relaxed);
        }
    }
    if (itr != _phase_2_list.begin()) {
        _phase_2_list.erase(_phase_2_list.begin(), itr);
    }
}

template <typename T, bool track_bytes_held, bool use_deque>
GenerationHoldList<T, track_bytes_held, use_deque>::GenerationHoldList()
    : _phase_1_list(),
      _phase_2_list(),
      _held_bytes()
{
}

template <typename T, bool track_bytes_held, bool use_deque>
GenerationHoldList<T, track_bytes_held, use_deque>::~GenerationHoldList()
{
    assert(_phase_1_list.empty());
    assert(_phase_2_list.empty());
    assert(get_held_bytes() == 0);
}

template <typename T, bool track_bytes_held, bool use_deque>
void
GenerationHoldList<T, track_bytes_held, use_deque>::insert(T data)
{
    _phase_1_list.push_back(std::move(data));
    if constexpr (track_bytes_held) {
        _held_bytes.store(get_held_bytes() + _phase_1_list.back()->byte_size(), std::memory_order_relaxed);
    }
}

template <typename T, bool track_bytes_held, bool use_deque>
void
GenerationHoldList<T, track_bytes_held, use_deque>::reclaim_all()
{
    _phase_1_list.clear();
    _phase_2_list.clear();
    _held_bytes = 0;
}

template <typename T, bool track_bytes_held, bool use_deque>
template <typename Func>
void
GenerationHoldList<T, track_bytes_held, use_deque>::reclaim_all(Func func)
{
    for (const auto& elem_with_gen : _phase_2_list) {
        func(elem_with_gen.elem);
    }
    reclaim_all();
}

}
