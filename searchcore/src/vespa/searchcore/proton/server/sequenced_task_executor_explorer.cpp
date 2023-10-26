// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sequenced_task_executor_explorer.h"
#include "executor_explorer_utils.h"
#include <vespa/vespalib/data/slime/cursor.h>
#include <vespa/vespalib/util/sequencedtaskexecutor.h>

using vespalib::ISequencedTaskExecutor;
using vespalib::SequencedTaskExecutor;
using vespalib::slime::Cursor;

namespace proton {

using explorer::convert_executor_to_slime;

SequencedTaskExecutorExplorer::SequencedTaskExecutorExplorer(vespalib::ISequencedTaskExecutor *executor)
    : _executor(executor)
{
}

namespace {

void
convert_raw_executor_stats_to_slime(ISequencedTaskExecutor* executor, Cursor& array)
{
    if (executor == nullptr) {
        return;
    }
    auto* seq = dynamic_cast<SequencedTaskExecutor*>(executor);
    if (!seq) {
        return;
    }
    auto raw_stats = seq->get_raw_stats();
    for (size_t executor_id = 0; executor_id < raw_stats.size(); ++executor_id) {
        const auto& stats = raw_stats[executor_id];
        auto& obj = array.addObject();
        obj.setLong("executor_id", executor_id);
        obj.setDouble("saturation", stats.get_saturation());
        obj.setDouble("utilization", stats.getUtil());
        obj.setLong("accepted_tasks", stats.acceptedTasks);
        obj.setLong("rejected_tasks", stats.rejectedTasks);
        obj.setLong("wakeups", stats.wakeupCount);
        auto& qs = obj.setObject("queue_size");
        qs.setLong("min", stats.queueSize.min());
        qs.setLong("max", stats.queueSize.max());
        qs.setLong("count", stats.queueSize.count());
        qs.setLong("total", stats.queueSize.total());
        qs.setDouble("average", stats.queueSize.average());
    }
}

}

void
SequencedTaskExecutorExplorer::get_state(const vespalib::slime::Inserter& inserter, bool full) const
{
    auto& object = inserter.insertObject();
    convert_executor_to_slime(_executor, object);
    if (full) {
        convert_raw_executor_stats_to_slime(_executor, object.setArray("executors"));
    }
}

}
