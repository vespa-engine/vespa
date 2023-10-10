// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "searchiterator.h"
#include <vespa/searchlib/common/bitvector.h>

namespace search::queryeval {

/**
* Helper methods for doing termwise evaluation.
**/
class TermwiseHelper {
public:
    template<typename IT>
    static BitVector::UP andChildren(BitVector::UP result, IT from, IT to, uint32_t begin_id);
    template<typename IT>
    static void andChildren(BitVector & result, IT from, IT to, uint32_t begin_id);
    template<typename IT>
    static BitVector::UP andChildren(IT from, IT to, uint32_t begin_id);

    template<typename IT>
    static BitVector::UP orChildren(BitVector::UP result, IT from, IT to, uint32_t begin_id);
    template<typename IT>
    static void orChildren(BitVector & result, IT from, IT to, uint32_t begin_id);
    template<typename IT>
    static BitVector::UP orChildren(IT from, IT to, uint32_t begin_id);
private:
    template<typename IT>
    static BitVector::UP andIterators(BitVector::UP result, IT begin, IT end, uint32_t begin_id, bool select_bitvector);
    template<typename IT>
    static void andIterators(BitVector & result, IT begin, IT end, uint32_t begin_id, bool select_bitvector);
    template<typename IT>
    static BitVector::UP orIterators(BitVector::UP result, IT begin, IT end, uint32_t begin_id, bool select_bitvector);
    template<typename IT>
    static void orIterators(BitVector & result, IT begin, IT end, uint32_t begin_id, bool select_bitvector);
};

template<typename IT>
BitVector::UP
TermwiseHelper::andChildren(BitVector::UP result, IT from, IT to, uint32_t begin_id) {
    return andIterators(andIterators(std::move(result), from, to, begin_id, true), from, to, begin_id, false);
}

template<typename IT>
void
TermwiseHelper::andChildren(BitVector & result, IT from, IT to, uint32_t begin_id) {
    andIterators(result, from, to, begin_id, true);
    andIterators(result, from, to, begin_id, false);
}

template<typename IT>
BitVector::UP
TermwiseHelper::andChildren(IT from, IT to, uint32_t begin_id) {
    return andChildren(BitVector::UP(), from, to, begin_id);
}

template<typename IT>
BitVector::UP
TermwiseHelper::orChildren(BitVector::UP result, IT from, IT to, uint32_t begin_id) {
    return orIterators(orIterators(std::move(result), from, to, begin_id, true),
                       from, to, begin_id, false);
}

template<typename IT>
void
TermwiseHelper::orChildren(BitVector & result, IT from, IT to, uint32_t begin_id) {
    orIterators(result, from, to, begin_id, true);
    orIterators(result, from, to, begin_id, false);
}

template<typename IT>
BitVector::UP
TermwiseHelper::orChildren(IT from, IT to, uint32_t begin_id) {
    return orChildren(BitVector::UP(), from, to, begin_id);
}

template<typename IT>
BitVector::UP
TermwiseHelper::andIterators(BitVector::UP result, IT begin, IT end, uint32_t begin_id, bool select_bitvector) {
    for (IT it(begin); it != end; ++it) {
        auto & child = *it;
        if (child->isBitVector() == select_bitvector) {
            if (!result) {
                result = child->get_hits(begin_id);
            } else {
                child->and_hits_into(*result, begin_id);
            }
        }
    }
    return result;
}

template<typename IT>
void
TermwiseHelper::andIterators(BitVector & result, IT begin, IT end, uint32_t begin_id, bool select_bitvector) {
    for (IT it(begin); it != end; ++it) {
        auto & child = *it;
        if (child->isBitVector() == select_bitvector) {
            child->and_hits_into(result, begin_id);
        }
    }
}

template<typename IT>
BitVector::UP
TermwiseHelper::orIterators(BitVector::UP result, IT begin, IT end, uint32_t begin_id, bool select_bitvector) {
    for (IT it(begin); it != end; ++it) {
        auto & child = *it;
        if (child->isBitVector() == select_bitvector) {
            if (!result) {
                result = child->get_hits(begin_id);
            } else {
                child->or_hits_into(*result, begin_id);
            }
        }
    }
    return result;
}

template<typename IT>
void
TermwiseHelper::orIterators(BitVector & result, IT begin, IT end, uint32_t begin_id, bool select_bitvector) {
    for (IT it(begin); it != end; ++it) {
        auto & child = *it;
        if (child->isBitVector() == select_bitvector) {
            child->or_hits_into(result, begin_id);
        }
    }
}

}
