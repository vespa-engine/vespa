// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "compaction_strategy.h"
#include "compaction_spec.h"
#include <vespa/vespalib/util/memoryusage.h>
#include <vespa/vespalib/util/address_space.h>
#include <iostream>
#include <limits>

namespace vespalib::datastore {

bool
CompactionStrategy::should_compact_memory(const MemoryUsage& memory_usage) const
{
    return should_compact_memory(memory_usage.usedBytes(), memory_usage.deadBytes());
}

bool
CompactionStrategy::should_compact_address_space(const AddressSpace& address_space) const
{
    return should_compact_address_space(address_space.used(), address_space.dead());
}

CompactionSpec
CompactionStrategy::should_compact(const MemoryUsage& memory_usage, const AddressSpace& address_space) const
{
    return CompactionSpec(should_compact_memory(memory_usage), should_compact_address_space(address_space));
}

std::ostream& operator<<(std::ostream& os, const CompactionStrategy& compaction_strategy)
{
    os << "{maxDeadBytesRatio=" << compaction_strategy.getMaxDeadBytesRatio() <<
        ", maxDeadAddressSpaceRatio=" << compaction_strategy.getMaxDeadAddressSpaceRatio() <<
        "}";
    return os;
}

CompactionStrategy
CompactionStrategy::make_compact_all_active_buffers_strategy()
{
    return CompactionStrategy(0.0, 0.0, std::numeric_limits<uint32_t>::max(), 1.0);
}

}
