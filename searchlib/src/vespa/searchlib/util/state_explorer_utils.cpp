// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "state_explorer_utils.h"
#include <vespa/searchcommon/attribute/status.h>
#include <vespa/vespalib/data/slime/cursor.h>
#include <vespa/vespalib/util/memoryusage.h>

using search::attribute::Status;
using vespalib::MemoryUsage;
using vespalib::slime::Cursor;

namespace search {

void
StateExplorerUtils::memory_usage_to_slime(const MemoryUsage& usage, Cursor& object)
{
    object.setLong("allocated", usage.allocatedBytes());
    object.setLong("used", usage.usedBytes());
    object.setLong("dead", usage.deadBytes());
    object.setLong("onHold", usage.allocatedBytesOnHold());
}

void
StateExplorerUtils::status_to_slime(const Status &status, Cursor &object)
{
    object.setLong("numDocs", status.getNumDocs());
    object.setLong("numValues", status.getNumValues());
    object.setLong("numUniqueValues", status.getNumUniqueValues());
    object.setLong("lastSerialNum", status.getLastSyncToken());
    object.setLong("updateCount", status.getUpdateCount());
    object.setLong("nonIdempotentUpdateCount", status.getNonIdempotentUpdateCount());
    object.setLong("bitVectors", status.getBitVectors());
    {
        Cursor &memory = object.setObject("memoryUsage");
        memory.setLong("allocatedBytes", status.getAllocated());
        memory.setLong("usedBytes", status.getUsed());
        memory.setLong("deadBytes", status.getDead());
        memory.setLong("onHoldBytes", status.getOnHold());
        memory.setLong("onHoldBytesMax", status.getOnHoldMax());
    }
}

}
