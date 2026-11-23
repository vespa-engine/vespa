// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "shrink_summary_lid_space_flush_target.h"

#include <vespa/searchcorespi/common/tracing.h>
#include <vespa/vespalib/util/lambdatask.h>

using searchcorespi::common::get_tracer;
using vespalib::makeLambdaTask;

namespace proton {

namespace {

const std::string shrink_docstore_lid_space_trace_span_name("Shrink docstore lid space");
const std::string shrink_summary_name("summary.shrink");

} // namespace

ShrinkSummaryLidSpaceFlushTarget::ShrinkSummaryLidSpaceFlushTarget(
    std::shared_ptr<const DocTypeAndSubDbName> doc_type_and_subdb_name_in, SerialNum flushedSerialNum,
    vespalib::Executor& summaryService, std::shared_ptr<ICompactableLidSpace> target)
    : ShrinkLidSpaceFlushTarget(shrink_summary_name, Type::GC, Component::DOCUMENT_STORE,
                                std::move(doc_type_and_subdb_name_in), flushedSerialNum,
                                vespalib::system_clock::now(), std::move(target)),
      _summaryService(summaryService) {
}

ShrinkSummaryLidSpaceFlushTarget::~ShrinkSummaryLidSpaceFlushTarget() = default;

void ShrinkSummaryLidSpaceFlushTarget::init_flush(SerialNum                            currentSerial,
                                                  std::shared_ptr<search::IFlushToken> flush_token,
                                                  TaskPromise                          task_promise) {
    // Called by document db main executor.
    auto        tracer = get_tracer();
    auto        parent_span = tracer->GetCurrentSpan();
    TaskPromise proxied_task_promise;
    auto        future_flush_task = proxied_task_promise.get_future();
    _summaryService.execute(makeLambdaTask([this, currentSerial, flush_token(std::move(flush_token)),
                                            proxied_task_promise(std::move(proxied_task_promise)),
                                            tracer(std::move(tracer)),
                                            parent_span(std::move(parent_span))]() mutable {
        auto scope = tracer->WithActiveSpan(parent_span);
        ShrinkLidSpaceFlushTarget::init_flush(currentSerial, std::move(flush_token), std::move(proxied_task_promise));
    }));
    task_promise.set_value(future_flush_task.get());
}

[[nodiscard]] std::string_view ShrinkSummaryLidSpaceFlushTarget::trace_span_name() const noexcept {
    return shrink_docstore_lid_space_trace_span_name;
}

} // namespace proton
