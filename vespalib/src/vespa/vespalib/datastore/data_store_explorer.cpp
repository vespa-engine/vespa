// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "data_store_explorer.h"
#include "datastorebase.h"
#include <vespa/vespalib/data/slime/cursor.h>
#include <vespa/vespalib/util/memoryusage.h>
#include <vespa/vespalib/util/state_explorer_utils.h>

using vespalib::slime::Cursor;

namespace vespalib::datastore {

DataStoreExplorer::DataStoreExplorer(const DataStoreBase &store)
    : StateExplorer(),
      _store(store)
{
}

DataStoreExplorer::~DataStoreExplorer() = default;

void
DataStoreExplorer::get_state(const vespalib::slime::Inserter& inserter, bool full) const
{
    (void) full;
    auto& object = inserter.insertObject();
    StateExplorerUtils::memory_usage_to_slime(_store.getMemoryUsage(), object.setObject("memory_usage"));
    auto bufferid_limit = _store.get_bufferid_limit_acquire();
    auto max_num_buffers = _store.getMaxNumBuffers();
    object.setLong("bufferid_limit", bufferid_limit);
    object.setLong("max_num_buffers", max_num_buffers);
}

}
