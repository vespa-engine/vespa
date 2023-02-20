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
    struct PreCalculated {
        PreCalculated(uint32_t range);
        ~PreCalculated();
        std::vector<ResultSet> _ands;
        std::vector<ResultSet> _ors;
        std::vector<ResultSet> _nots;
    };
    static const PreCalculated _preCalc;
public:
    ResultSet() noexcept : _val(0u) { }

    static uint32_t enumToMask(uint32_t rhs) noexcept {
        return 1u << rhs;
    }

    static uint32_t illegalMask() noexcept {
        return (1u << Result::enumRange);
    }

    void add(const Result &rhs) {
        _val |= enumToMask(rhs.toEnum());
    }

    [[nodiscard]] bool hasEnum(uint32_t rhs) const {
        return (_val & enumToMask(rhs)) != 0u;
    }

    bool operator==(const ResultSet &rhs) const {
        return _val == rhs._val;
    }

    bool operator!=(const ResultSet &rhs) const {
        return _val != rhs._val;
    }

    // calculcate set of results emitted by document selection and operator.
    [[nodiscard]] ResultSet calcAnd(const ResultSet &rhs) const {
        return _preCalc._ands[(_val << Result::enumRange) | rhs._val];
    }

    // calculcate set of results emitted by document selection or operator.
    [[nodiscard]] ResultSet calcOr(const ResultSet &rhs) const {
        return _preCalc._ors[(_val << Result::enumRange) | rhs._val];
    }

    // calculcate set of results emitted by document selection not operator.
    [[nodiscard]] ResultSet calcNot() const { return _preCalc._nots[_val]; }

    void clear() { _val = 0; }
    void fill() { _val = illegalMask() - 1; }
    static void preCalc();
private:
    // precalc helper methods
    void pcnext() noexcept { ++_val; }
    [[nodiscard]] bool pcvalid() const noexcept { return _val < illegalMask(); }
};

}
