// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <iosfwd>
#include <cstdint>

namespace vespalib {

class AddressSpace;
class MemoryUsage;

}

namespace vespalib::datastore {

class CompactionSpec;

/*
 * Class describing compaction strategy for a compactable data structure.
 */
class CompactionStrategy
{
public:
    static constexpr size_t DEAD_BYTES_SLACK = 0x10000u;
    static constexpr size_t DEAD_ADDRESS_SPACE_SLACK = 0x10000u;
private:
    double _maxDeadBytesRatio; // Max ratio of dead bytes before compaction
    double _maxDeadAddressSpaceRatio; // Max ratio of dead address space before compaction
    uint32_t _max_buffers; // Max number of buffers to compact for each reason (memory usage, address space usage)
    double _active_buffers_ratio; // Ratio of active buffers to compact for each reason (memory usage, address space usage)
    bool should_compact_memory(size_t used_bytes, size_t dead_bytes) const {
        return ((dead_bytes >= DEAD_BYTES_SLACK) &&
                (dead_bytes > used_bytes * getMaxDeadBytesRatio()));
    }
    bool should_compact_address_space(size_t used_address_space, size_t dead_address_space) const {
        return ((dead_address_space >= DEAD_ADDRESS_SPACE_SLACK) &&
                (dead_address_space > used_address_space * getMaxDeadAddressSpaceRatio()));
    }
public:
    CompactionStrategy() noexcept
        : _maxDeadBytesRatio(0.05),
          _maxDeadAddressSpaceRatio(0.2),
          _max_buffers(1),
          _active_buffers_ratio(0.1)
    {
    }
    CompactionStrategy(double maxDeadBytesRatio, double maxDeadAddressSpaceRatio) noexcept
        : _maxDeadBytesRatio(maxDeadBytesRatio),
          _maxDeadAddressSpaceRatio(maxDeadAddressSpaceRatio),
          _max_buffers(1),
          _active_buffers_ratio(0.1)
    {
    }
    CompactionStrategy(double maxDeadBytesRatio, double maxDeadAddressSpaceRatio, uint32_t max_buffers, double active_buffers_ratio) noexcept
        : _maxDeadBytesRatio(maxDeadBytesRatio),
          _maxDeadAddressSpaceRatio(maxDeadAddressSpaceRatio),
          _max_buffers(max_buffers),
          _active_buffers_ratio(active_buffers_ratio)
    {
    }
    double getMaxDeadBytesRatio() const { return _maxDeadBytesRatio; }
    double getMaxDeadAddressSpaceRatio() const { return _maxDeadAddressSpaceRatio; }
    uint32_t get_max_buffers() const noexcept { return _max_buffers; }
    double get_active_buffers_ratio() const noexcept { return _active_buffers_ratio; }
    bool operator==(const CompactionStrategy & rhs) const {
        return (_maxDeadBytesRatio == rhs._maxDeadBytesRatio) &&
            (_maxDeadAddressSpaceRatio == rhs._maxDeadAddressSpaceRatio) &&
            (_max_buffers == rhs._max_buffers) &&
            (_active_buffers_ratio == rhs._active_buffers_ratio);
    }
    bool operator!=(const CompactionStrategy & rhs) const { return !(operator==(rhs)); }

    bool should_compact_memory(const MemoryUsage& memory_usage) const;
    bool should_compact_address_space(const AddressSpace& address_space) const;
    CompactionSpec should_compact(const MemoryUsage& memory_usage, const AddressSpace& address_space) const;
    static CompactionStrategy make_compact_all_active_buffers_strategy();
};

std::ostream& operator<<(std::ostream& os, const CompactionStrategy& compaction_strategy);

}
