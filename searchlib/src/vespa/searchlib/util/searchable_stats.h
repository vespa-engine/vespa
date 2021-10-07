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
    size_t _sizeOnDisk;
    size_t _max_component_size_on_disk;

public:
    SearchableStats() : _memoryUsage(), _docsInMemory(0), _sizeOnDisk(0), _max_component_size_on_disk(0) {}
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
        _max_component_size_on_disk = value;
        return *this;
    }
    size_t sizeOnDisk() const { return _sizeOnDisk; }

    /**
     * Returns the max disk size used by a single Searchable component,
     * e.g. among the components that are merged into a SearchableStats instance via merge().
     */
    size_t max_component_size_on_disk() const { return _max_component_size_on_disk; }

    SearchableStats &merge(const SearchableStats &rhs) {
        _memoryUsage.merge(rhs._memoryUsage);
        _docsInMemory += rhs._docsInMemory;
        _sizeOnDisk += rhs._sizeOnDisk;
        _max_component_size_on_disk = std::max(_max_component_size_on_disk, rhs._sizeOnDisk);
        return *this;
    }
};

} // namespace search

