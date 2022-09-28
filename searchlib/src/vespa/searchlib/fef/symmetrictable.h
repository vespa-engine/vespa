// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "table.h"
#include <vector>

namespace search {
namespace fef {

/**
 * This class represents a rank table with double values. It takes both negative and positive indexes.
 * The content of a table is typically a pre-computed function that is used by a feature executor.
 * Values in the negative index range are negated values of corresponding positive value.
 **/
class SymmetricTable
{
private:
    std::vector<double> _backingTable;
    int                 _size;
    double *            _table;
    double              _max;

public:
    typedef std::shared_ptr<SymmetricTable> SP;

    SymmetricTable();
    /**
     * Creates a symmetric table based on the real one.
     **/
    SymmetricTable(const Table & table);
    SymmetricTable(const SymmetricTable & table);
    ~SymmetricTable();

    SymmetricTable & operator =(const SymmetricTable & table);
    void swap(SymmetricTable & rhs) {
        _backingTable.swap(rhs._backingTable);
        std::swap(_size, rhs._size);
        std::swap(_table, rhs._table);
        std::swap(_max, rhs._max);
    }
    /**
     * Returns the element at the given position.
     **/
    double operator[](int i) const { return _table[i]; }

    /**
     * Retrives the element at the given position or the last element if i is outside the range.
     **/
    double get(int i) const {
        return (i<-_size) ? _table[-_size] : ((i>_size) ? _table[_size] : _table[i]);
    };
    double max() const { return _max; }
};

} // namespace fef
} // namespace search

