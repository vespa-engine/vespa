// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/memoryusage.h>

namespace search {

/**
 * Simple statistics for a single Searchable component or multiple components that are merged together.
 *
 * E.g. used for internal aggregation before inserting numbers into the metrics framework.
 **/
class SearchableStats
{
private:
    vespalib::MemoryUsage _memoryUsage;
    size_t _docsInMemory;
    size_t _sizeOnDisk; // in bytes
    size_t _fusion_size_on_disk; // in bytes

public:
    SearchableStats() : _memoryUsage(), _docsInMemory(0), _sizeOnDisk(0), _fusion_size_on_disk(0) {}
    SearchableStats &memoryUsage(const vespalib::MemoryUsage &usage) {
        _memoryUsage = usage;
        return *this;
    }
    const vespalib::MemoryUsage &memoryUsage() const { return _memoryUsage; }
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
    SearchableStats& fusion_size_on_disk(size_t value) {
        _fusion_size_on_disk = value;
        return *this;
    }
    size_t fusion_size_on_disk() const { return _fusion_size_on_disk; }

    SearchableStats &merge(const SearchableStats &rhs) {
        _memoryUsage.merge(rhs._memoryUsage);
        _docsInMemory += rhs._docsInMemory;
        _sizeOnDisk += rhs._sizeOnDisk;
        _fusion_size_on_disk += rhs._fusion_size_on_disk;
        return *this;
    }
};

} // namespace search

