// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "field_index_stats.h"
#include <map>

namespace search {

/**
 * Simple statistics for a single index or for multiple indexes (merged stats).
 *
 * E.g. used for internal aggregation before inserting numbers into the metrics framework.
 **/
class IndexStats
{
private:
    vespalib::MemoryUsage _memoryUsage;
    size_t _docsInMemory;
    size_t _sizeOnDisk; // in bytes
    size_t _fusion_size_on_disk; // in bytes
    std::map<std::string, FieldIndexStats> _field_stats;

public:
    IndexStats();
    ~IndexStats();
    IndexStats &memoryUsage(const vespalib::MemoryUsage &usage) {
        _memoryUsage = usage;
        return *this;
    }
    const vespalib::MemoryUsage &memoryUsage() const { return _memoryUsage; }
    IndexStats &docsInMemory(size_t value) {
        _docsInMemory = value;
        return *this;
    }
    size_t docsInMemory() const { return _docsInMemory; }
    IndexStats &sizeOnDisk(size_t value) {
        _sizeOnDisk = value;
        return *this;
    }
    size_t sizeOnDisk() const { return _sizeOnDisk; }
    IndexStats& fusion_size_on_disk(size_t value) {
        _fusion_size_on_disk = value;
        return *this;
    }
    size_t fusion_size_on_disk() const { return _fusion_size_on_disk; }

    IndexStats& merge(const IndexStats& rhs);
    bool operator==(const IndexStats& rhs) const noexcept;
    IndexStats& add_field_stats(const std::string& name, const FieldIndexStats& stats);
    const std::map<std::string, FieldIndexStats>& get_field_stats() const noexcept { return _field_stats; }
};

std::ostream& operator<<(std::ostream& os, const IndexStats& stats);

}
