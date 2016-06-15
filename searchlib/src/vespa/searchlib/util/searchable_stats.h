// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

namespace search {

/**
 * Simple statistics for a single Searchable component. Used for
 * internal aggregation before inserting numbers into the metrics
 * framework.
 **/
class SearchableStats
{
private:
    size_t _memoryUsage;
    size_t _docsInMemory;
    size_t _sizeOnDisk;

public:
    SearchableStats() : _memoryUsage(0), _docsInMemory(0), _sizeOnDisk(0) {}
    SearchableStats &memoryUsage(size_t value) {
        _memoryUsage = value;
        return *this;
    }
    size_t memoryUsage() const { return _memoryUsage; }
    SearchableStats &docsInMemory(size_t value) {
        _docsInMemory = value;
        return *this;
    }
    size_t docsInMemory() const { return _docsInMemory; }
    SearchableStats &sizeOnDisk(size_t value) {
        _sizeOnDisk = value;
        return *this;
    }
    size_t sizeOnDisk() const { return _sizeOnDisk; }
    SearchableStats &add(const SearchableStats &rhs) {
        _memoryUsage += rhs._memoryUsage;
        _docsInMemory += rhs._docsInMemory;
        _sizeOnDisk += rhs._sizeOnDisk;
        return *this;
    }
};

} // namespace search

