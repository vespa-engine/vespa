// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "executor_explorer_utils.h"
#include "executor_threading_service_explorer.h"
#include "executorthreadingservice.h"
#include <vespa/vespalib/data/slime/cursor.h>
#include <vespa/vespalib/util/adaptive_sequenced_executor.h>
#include <vespa/vespalib/util/sequencedtaskexecutor.h>

using vespalib::AdaptiveSequencedExecutor;
using vespalib::ISequencedTaskExecutor;
using vespalib::SequencedTaskExecutor;
using vespalib::slime::Cursor;

namespace proton {

using explorer::convert_executor_to_slime;

namespace {

void
set_type(Cursor& object, const vespalib::string& type)
{
    object.setString("type", type);
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

