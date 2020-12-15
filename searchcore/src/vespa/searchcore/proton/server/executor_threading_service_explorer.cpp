// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "executor_threading_service_explorer.h"
#include "executorthreadingservice.h"
#include <vespa/vespalib/data/slime/cursor.h>
#include <vespa/vespalib/util/adaptive_sequenced_executor.h>
#include <vespa/vespalib/util/blockingthreadstackexecutor.h>
#include <vespa/vespalib/util/sequencedtaskexecutor.h>
#include <vespa/vespalib/util/singleexecutor.h>
#include <vespa/vespalib/util/threadexecutor.h>
#include <vespa/vespalib/util/threadstackexecutor.h>

using vespalib::AdaptiveSequencedExecutor;
using vespalib::BlockingThreadStackExecutor;
using vespalib::ISequencedTaskExecutor;
using vespalib::SequencedTaskExecutor;
using vespalib::SingleExecutor;
using vespalib::SyncableThreadExecutor;
using vespalib::ThreadStackExecutor;
using vespalib::slime::Cursor;

namespace proton {

namespace {

void
set_type(Cursor& object, const vespalib::string& type)
{
    object.setString("type", type);
}

void
convert_syncable_executor_to_slime(const SyncableThreadExecutor& executor, const vespalib::string& type, Cursor& object)
{
    set_type(object, type);
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

void
convert_executor_to_slime(const SyncableThreadExecutor* executor, Cursor& object)
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
        convert_syncable_executor_to_slime(*executor, "SyncableThreadExecutor", object);
    }
}

void
convert_sequenced_executor_to_slime(const SequencedTaskExecutor& executor, Cursor& object)
{
    set_type(object, "SequencedTaskExecutor");
    object.setLong("num_executors", executor.getNumExecutors());
    convert_executor_to_slime(executor.first_executor(), object.setObject("executor"));
}

void
convert_adaptive_executor_to_slime(const AdaptiveSequencedExecutor& executor, Cursor& object)
{
    set_type(object, "AdaptiveSequencedExecutor");
    object.setLong("num_strands", executor.getNumExecutors());
    auto cfg = executor.get_config();
    object.setLong("num_threads", cfg.num_threads);
    object.setLong("max_waiting", cfg.max_waiting);
    object.setLong("max_pending", cfg.max_pending);
    object.setLong("wakeup_limit", cfg.wakeup_limit);
}

void
convert_executor_to_slime(const ISequencedTaskExecutor* executor, Cursor& object)
{
    if (const auto* seq = dynamic_cast<const SequencedTaskExecutor*>(executor)) {
        convert_sequenced_executor_to_slime(*seq, object);
    } else if (const auto* ada = dynamic_cast<const AdaptiveSequencedExecutor*>(executor)) {
        convert_adaptive_executor_to_slime(*ada, object);
    } else {
        set_type(object, "ISequencedTaskExecutor");
        object.setLong("num_executors", executor->getNumExecutors());
    }
}

}

ExecutorThreadingServiceExplorer::ExecutorThreadingServiceExplorer(ExecutorThreadingService& service)
    : _service(service)
{
}

ExecutorThreadingServiceExplorer::~ExecutorThreadingServiceExplorer() = default;

void
ExecutorThreadingServiceExplorer::get_state(const vespalib::slime::Inserter& inserter, bool full) const
{
    auto& object = inserter.insertObject();
    if (full) {
        convert_executor_to_slime(&_service.getMasterExecutor(), object.setObject("master"));
        convert_executor_to_slime(&_service.getIndexExecutor(), object.setObject("index"));
        convert_executor_to_slime(&_service.getSummaryExecutor(), object.setObject("summary"));
        convert_executor_to_slime(&_service.indexFieldInverter(), object.setObject("index_field_inverter"));
        convert_executor_to_slime(&_service.indexFieldWriter(), object.setObject("index_field_writer"));
        convert_executor_to_slime(&_service.attributeFieldWriter(), object.setObject("attribute_field_writer"));
    }
}

}

