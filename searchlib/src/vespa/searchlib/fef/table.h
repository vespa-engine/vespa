// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vector>
#include <memory>

namespace search::fef {

/**
 * This class represents a rank table with double values.
 * The content of a table is typically a pre-computed function that is used by a feature executor.
 **/
class Table
{
private:
    std::vector<double> _table;
    double              _max;

public:
    using SP = std::shared_ptr<Table>;

    /**
     * Creates a new table with zero elements.
     **/
    Table();
    ~Table();

    /**
     * Adds the given element to this table.
     **/
    Table & add(double val) {
        _table.push_back(val);
        _max = std::max(val, _max);
        return *this;
    }

    /**
     * Returns the number of elements in this table.
     **/
    size_t size() const { return _table.size(); }

    /**
     * Returns the element at the given position.
     **/
    double operator[](size_t i) const { return _table[i]; }

    /**
     * Retrives the element at the given position or the last element if i is outside the range.
     **/
    double get(size_t i) const {
        return _table[std::min(i, size() - 1)];
    };

    /**
     * Returns the largest element in this table.
     **/
    double max() const {
        return _max;
    }
};

}
