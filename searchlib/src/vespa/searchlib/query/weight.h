// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstdint>

namespace search::query {

/**
 * Represents the weight given on a query item such as a term, phrase, or equiv.
 * Normally given and used as an integer percent value.
 */
class Weight
{
private:
    int32_t _weight;

public:
    /**
     * constructor.
     * @param value The initial weight in percent; should be 100 unless a specific value is set.
     **/
    explicit Weight(int32_t value) : _weight(value) {}

    /**
     * change the weight value.
     * @param value The new weight value in percent.
     **/
    void setPercent(int32_t value) { _weight = value; }

    /**
     * retrieve the weight value.
     * @return weight value in percent.
     **/
    int32_t percent() const  { return _weight; }

    /**
     * retrieve the weight value as a multiplier.
     * @return weight multiplier with 100 percent giving 1.0 as multiplier.
     **/
    double multiplier() const { return 0.01 * _weight; }

    /** compare two weights */
    bool operator== (const Weight& other) const { return _weight == other._weight; }
};

}

inline search::query::Weight operator+(const search::query::Weight& a, const search::query::Weight& b)
{
    return search::query::Weight(a.percent() + b.percent());
}

