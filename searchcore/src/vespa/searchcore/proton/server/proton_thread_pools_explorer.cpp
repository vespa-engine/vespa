// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "executor_explorer_utils.h"
#include "proton_thread_pools_explorer.h"
#include <vespa/vespalib/data/slime/cursor.h>
#include <vespa/vespalib/util/threadexecutor.h>

using vespalib::SyncableThreadExecutor;

namespace proton {

using explorer::convert_executor_to_slime;

ProtonThreadPoolsExplorer::ProtonThreadPoolsExplorer(const SyncableThreadExecutor* shared,
                                                     const SyncableThreadExecutor* match,
                                                     const SyncableThreadExecutor* docsum,
                                                     const SyncableThreadExecutor* flush,
                                                     const SyncableThreadExecutor* proton,
                                                     const SyncableThreadExecutor* warmup)
    : _shared(shared),
      _match(match),
      _docsum(docsum),
      _flush(flush),
      _proton(proton),
      _warmup(warmup)
{
}

void
ProtonThreadPoolsExplorer::get_state(const vespalib::slime::Inserter& inserter, bool full) const
{
    auto& object = inserter.insertObject();
    if (full) {
        convert_executor_to_slime(_shared, object.setObject("shared"));
        convert_executor_to_slime(_match, object.setObject("match"));
        convert_executor_to_slime(_docsum, object.setObject("docsum"));
        convert_executor_to_slime(_flush, object.setObject("flush"));
        convert_executor_to_slime(_proton, object.setObject("proton"));
        convert_executor_to_slime(_warmup, object.setObject("warmup"));
    }
}

}
