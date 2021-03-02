// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "executor_explorer_utils.h"
#include <vespa/vespalib/data/slime/cursor.h>
#include <vespa/vespalib/util/blockingthreadstackexecutor.h>
#include <vespa/vespalib/util/singleexecutor.h>
#include <vespa/vespalib/util/threadstackexecutor.h>

using vespalib::BlockingThreadStackExecutor;
using vespalib::SingleExecutor;
using vespalib::ThreadExecutor;
using vespalib::ThreadStackExecutor;
using vespalib::slime::Cursor;


namespace proton::explorer {

namespace {

void
convert_syncable_executor_to_slime(const ThreadExecutor& executor, const vespalib::string& type, Cursor& object)
{
    object.setString("type", type);
    object.setLong("num_threads", executor.getNumThreads());
    object.setLong("task_limit", executor.getTaskLimit());
}

void
convert_single_executor_to_slime(const SingleExecutor& executor, Cursor& object)
{
    convert_syncable_executor_to_slime(executor, "SingleExecutor", object);
    object.setLong("watermark", executor.get_watermark());
    object.setDouble("reaction_time_sec", vespalib::to_s(executor.get_reaction_time()));
}

}

void
convert_executor_to_slime(const ThreadExecutor* executor, Cursor& object)
{
    if (executor == nullptr) {
        return;
    }
    if (const auto* single = dynamic_cast<const SingleExecutor*>(executor)) {
        convert_single_executor_to_slime(*single, object);
    } else if (const auto* blocking = dynamic_cast<const BlockingThreadStackExecutor*>(executor)) {
        convert_syncable_executor_to_slime(*blocking, "BlockingThreadStackExecutor", object);
    } else if (const auto* thread = dynamic_cast<const ThreadStackExecutor*>(executor)) {
        convert_syncable_executor_to_slime(*thread, "ThreadStackExecutor", object);
    } else {
        convert_syncable_executor_to_slime(*executor, "ThreadExecutor", object);
    }
}

}

