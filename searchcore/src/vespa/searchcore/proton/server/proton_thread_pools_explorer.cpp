// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "proton_thread_pools_explorer.h"
#include "executor_explorer_utils.h"
#include "sequenced_task_executor_explorer.h"
#include <vespa/vespalib/data/slime/cursor.h>
#include <vespa/vespalib/util/threadexecutor.h>

using vespalib::ThreadExecutor;

namespace proton {

using explorer::convert_executor_to_slime;

ProtonThreadPoolsExplorer::ProtonThreadPoolsExplorer(const ThreadExecutor* shared,
                                                     const ThreadExecutor* match,
                                                     const ThreadExecutor* docsum,
                                                     const ThreadExecutor* flush,
                                                     const ThreadExecutor* proton,
                                                     vespalib::ISequencedTaskExecutor* field_writer)
    : _shared(shared),
      _match(match),
      _docsum(docsum),
      _flush(flush),
      _proton(proton),
      _field_writer(field_writer)
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
    }
}

const vespalib::string FIELD_WRITER = "field_writer";

std::vector<vespalib::string>
ProtonThreadPoolsExplorer::get_children_names() const
{
    return {FIELD_WRITER};
}

std::unique_ptr<vespalib::StateExplorer>
ProtonThreadPoolsExplorer::get_child(vespalib::stringref name) const
{
    if (name == FIELD_WRITER) {
        return std::make_unique<SequencedTaskExecutorExplorer>(_field_writer);
    }
    return {};
}

}
