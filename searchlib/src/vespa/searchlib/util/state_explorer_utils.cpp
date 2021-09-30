// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "state_explorer_utils.h"
#include <vespa/vespalib/data/slime/cursor.h>
#include <vespa/vespalib/util/memoryusage.h>

namespace search {

void
StateExplorerUtils::memory_usage_to_slime(const vespalib::MemoryUsage& usage, vespalib::slime::Cursor& object)
{
    object.setLong("allocated", usage.allocatedBytes());
    object.setLong("used", usage.usedBytes());
    object.setLong("dead", usage.deadBytes());
    object.setLong("onHold", usage.allocatedBytesOnHold());
}

}

