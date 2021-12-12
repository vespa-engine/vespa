// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "result.h"
#include <vector>

namespace document::select {

/*
 * Contains possible values of operations returning a Result.
 */

class ResultSet
{
    // bitvector of the enum values for possible results
    uint8_t _val; // range [0..7], cf. Result::toEnum()

    // Precalculated results for boolean operations on Result.
    static std::vector<ResultSet> _ands;
    static std::vector<ResultSet> _ors;
    static std::vector<ResultSet> _nots;
public:
    ResultSet() noexcept : _val(0u) { }

    static uint32_t enumToMask(uint32_t rhs) {
        return 1u << rhs;
    }

    static uint32_t illegalMask() {
        return (1u << Result::enumRange);
    }

    void add(const Result &rhs) {
        _val |= enumToMask(rhs.toEnum());
    }

    bool hasEnum(uint32_t rhs) const {
        return (_val & enumToMask(rhs)) != 0u;
    }

    bool hasResult(const Result &rhs) const {
        return hasEnum(rhs.toEnum());
    }

    bool operator==(const ResultSet &rhs) const {
        return _val == rhs._val;
    }

    bool operator!=(const ResultSet &rhs) const {
        return _val != rhs._val;
    }

    // calculcate set of results emitted by document selection and operator.
    ResultSet calcAnd(const ResultSet &rhs) const {
        return _ands[(_val << Result::enumRange) | rhs._val];
    }

    // calculcate set of results emitted by document selection or operator.
    ResultSet calcOr(const ResultSet &rhs) const {
        return _ors[(_val << Result::enumRange) | rhs._val];
    }

    // calculcate set of results emitted by document selection not operator.
    ResultSet calcNot() const { return _nots[_val]; }

    void clear() { _val = 0; }
    void fill() { _val = illegalMask() - 1; }
    static void preCalc();
private:
    // precalc helper methods
    void pcnext(void) { ++_val; }
    bool pcvalid(void) const { return _val < illegalMask(); }
};

}
