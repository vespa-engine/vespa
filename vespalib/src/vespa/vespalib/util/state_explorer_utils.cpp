// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "state_explorer_utils.h"
#include "address_space.h"
#include "memoryusage.h"
#include <vespa/vespalib/data/slime/cursor.h>

using vespalib::slime::Cursor;

namespace vespalib {

void
StateExplorerUtils::address_space_to_slime(const AddressSpace& address_space, slime::Cursor& object)
{
    object.setDouble("usage", address_space.usage());
    object.setLong("used", address_space.used());
    object.setLong("dead", address_space.dead());
    object.setLong("limit", address_space.limit());
}

void
StateExplorerUtils::memory_usage_to_slime(const MemoryUsage& usage, Cursor& object)
{
    object.setLong("allocated", usage.allocatedBytes());
    object.setLong("used", usage.usedBytes());
    object.setLong("dead", usage.deadBytes());
    object.setLong("onHold", usage.allocatedBytesOnHold());
}

}
