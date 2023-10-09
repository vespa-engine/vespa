// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "symmetrictable.h"

namespace search::fef {

SymmetricTable::SymmetricTable() :
    _backingTable(),
    _size(),
    _table(nullptr),
    _max(0)
{
}

SymmetricTable::SymmetricTable(const SymmetricTable & table) :
    _backingTable(table._backingTable),
    _size(_backingTable.size()/2),
    _table(&_backingTable[_size]),
    _max(table.max())
{
}

SymmetricTable & SymmetricTable::operator=(const SymmetricTable & rhs)
{
    if (&rhs != this) {
        SymmetricTable n(rhs);
        swap(n);
    }
    return *this;
}

SymmetricTable::SymmetricTable(const Table & table) :
    _backingTable(table.size()*2 - 1),
    _size(_backingTable.size()/2),
    _table(&_backingTable[_size]),
    _max(table.max())
{
    _table[0] = table[0];
    for(int i(1); i <= _size; i++) {
        _table[i] = table[i];
        _table[-i] = -table[i];
    }
}

SymmetricTable::~SymmetricTable() = default;

}
