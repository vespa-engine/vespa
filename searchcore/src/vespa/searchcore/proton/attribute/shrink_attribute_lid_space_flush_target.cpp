// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "shrink_attribute_lid_space_flush_target.h"

#include <vespa/searchcorespi/common/tracing.h>

using searchcorespi::common::get_tracer;

namespace proton {

namespace {

const std::string shrink_attribute_lid_space_trace_span_name("Shrink attribute lid space");
const std::string shrink_attribute_name_prefix("attribute.shrink.");

} // namespace

ShrinkAttributeLidSpaceFlushTarget::ShrinkAttributeLidSpaceFlushTarget(
    std::shared_ptr<const DocTypeAndSubDbName> doc_type_and_subdb_name_in, const std::string& attribute_name_in,
    SerialNum flushedSerialNum, std::shared_ptr<ICompactableLidSpace> target, ISequencedTaskExecutor& executor,
    ISequencedTaskExecutor::ExecutorId executor_id)
    : ShrinkLidSpaceFlushTarget(shrink_attribute_name_prefix + attribute_name_in, Type::GC, Component::ATTRIBUTE,
                                std::move(doc_type_and_subdb_name_in), flushedSerialNum,
                                vespalib::system_clock::now(), std::move(target)),
      _attribute_name(attribute_name_in),
      _executor(executor),
      _executor_id(executor_id) {
}

ShrinkAttributeLidSpaceFlushTarget::~ShrinkAttributeLidSpaceFlushTarget() = default;

void ShrinkAttributeLidSpaceFlushTarget::init_flush(SerialNum                            currentSerial,
                                                    std::shared_ptr<search::IFlushToken> flush_token,
                                                    TaskPromise                          task_promise) {
    // Called by document db main executor.
    auto        tracer = get_tracer();
    auto        parent_span = tracer->GetCurrentSpan();
    TaskPromise proxied_task_promise;
    auto        future_flush_task = proxied_task_promise.get_future();
    _executor.executeLambda(_executor_id, [this, currentSerial, flush_token(std::move(flush_token)),
                                           proxied_task_promise(std::move(proxied_task_promise)),
                                           tracer(std::move(tracer)), parent_span(std::move(parent_span))]() mutable {
        auto scope = tracer->WithActiveSpan(parent_span);
        ShrinkLidSpaceFlushTarget::init_flush(currentSerial, std::move(flush_token), std::move(proxied_task_promise));
    });
    task_promise.set_value(future_flush_task.get());
}

[[nodiscard]] std::string_view ShrinkAttributeLidSpaceFlushTarget::trace_span_name() const noexcept {
    return shrink_attribute_lid_space_trace_span_name;
}

[[nodiscard]] std::string_view ShrinkAttributeLidSpaceFlushTarget::attribute_name() const noexcept {
    return _attribute_name;
}

} // namespace proton
