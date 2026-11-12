// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "proto_telemetry_server_carrier.h"

#include <opentelemetry/trace/tracer.h>

#include <memory>
#include <string_view>

namespace vespa_opentelemetry {

/*
 * Context used by an rpc server to conditionally add spans below the client span on the client.
 */

class RpcRequestTelemetryContext {
public:
    using Span = opentelemetry::trace::Span;
    using Tracer = opentelemetry::trace::Tracer;

    RpcRequestTelemetryContext();
    RpcRequestTelemetryContext(const RpcRequestTelemetryContext&) = delete;
    RpcRequestTelemetryContext(RpcRequestTelemetryContext&&) = delete;
    ~RpcRequestTelemetryContext();
    RpcRequestTelemetryContext& operator=(const RpcRequestTelemetryContext&) = delete;
    RpcRequestTelemetryContext& operator=(RpcRequestTelemetryContext&&) = delete;

    void setup(const ProtoTelemetryServerCarrier& carrier, std::string_view tracer_name, std::string_view span_name);
    void clear();

    [[nodiscard]] const std::shared_ptr<Span>& span() const noexcept { return _span; }

private:
    std::shared_ptr<Span> _span;
};

} // namespace vespa_opentelemetry
