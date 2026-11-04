// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "proto_telemetry_server_carrier.h"

namespace vespa_opentelemetry {

ProtoTelemetryServerCarrier::ProtoTelemetryServerCarrier(const Map& map) noexcept : _map(map) {
}

ProtoTelemetryServerCarrier::~ProtoTelemetryServerCarrier() = default;

bool ProtoTelemetryServerCarrier::empty() const noexcept {
    return _map.empty();
}

opentelemetry::nostd::string_view
ProtoTelemetryServerCarrier::Get(opentelemetry::nostd::string_view key) const noexcept {
    auto it = _map.find(std::string(key));
    if (it != _map.end()) {
        return opentelemetry::nostd::string_view(it->second);
    }
    return "";
}

void ProtoTelemetryServerCarrier::Set(opentelemetry::nostd::string_view, opentelemetry::nostd::string_view) noexcept {
}

} // namespace vespa_opentelemetry
