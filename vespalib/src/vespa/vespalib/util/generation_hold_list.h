// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "generationhandler.h"
#include <atomic>
#include <deque>
#include <vector>

namespace vespalib {

/**
 * Class used to hold data elements until they can be safely reclaimed when they are no longer accessed by readers.
 *
 * This class must be used in accordance with a GenerationHandler.
 */
template <typename T, bool track_bytes_held, bool use_deque>
class GenerationHoldList {
private:
    using generation_t = vespalib::GenerationHandler::generation_t;

    struct ElemWithGen {
        T elem;
        generation_t gen;
        ElemWithGen(T elem_in, generation_t gen_in)
            : elem(std::move(elem_in)),
              gen(gen_in)
        {}
        size_t byte_size() const {
            if constexpr (track_bytes_held) {
                return elem->byte_size();
            }
            return 0;
        }
    };

    struct NoopFunc { void operator()(const T&){} };

    using ElemList = std::vector<T>;
    using ElemWithGenList = std::conditional_t<use_deque,
            std::deque<ElemWithGen>,
            std::vector<ElemWithGen>>;

    ElemList _phase_1_list;
    ElemWithGenList _phase_2_list;
    std::atomic<size_t> _held_bytes;

    /**
     * Transfer elements from phase 1 to phase 2 list, assigning the current generation.
     */
    void assign_generation_internal(generation_t current_gen);

    template<typename Func>
    void reclaim_internal(generation_t oldest_used_gen, Func callback);

public:
    GenerationHoldList();
    ~GenerationHoldList();

    /**
     * Insert the given data element on this hold list.
     */
    void insert(T data);

    /**
     * Assign the current generation to all data elements inserted on the hold list
     * since the last time this function was called.
     */
    void assign_generation(generation_t current_gen) {
        if (!_phase_1_list.empty()) {
            assign_generation_internal(current_gen);
        }
    }

    /**
     * Reclaim all data elements where the assigned generation < oldest used generation.
     * The callback function is called for each data element reclaimed.
     **/
    template<typename Func>
    void reclaim(generation_t oldest_used_gen, Func callback) {
        if (!_phase_2_list.empty() && (_phase_2_list.front().gen < oldest_used_gen)) {
            reclaim_internal(oldest_used_gen, callback);
        }
    }

    void reclaim(generation_t oldest_used_gen) {
        reclaim(oldest_used_gen, NoopFunc());
    }

    /**
     * Reclaim all data elements from this hold list.
     */
    void reclaim_all();

    /**
     * Reclaim all data elements from this hold list.
     * The callback function is called for all data elements reclaimed.
     */
    template<typename Func>
    void reclaim_all(Func callback);

    size_t get_held_bytes() const { return _held_bytes.load(std::memory_order_relaxed); }

    // Static size of _phase_2_list might depend on std::deque implementation
    static constexpr size_t sizeof_phase_2_list = sizeof(ElemWithGenList);
};

}
