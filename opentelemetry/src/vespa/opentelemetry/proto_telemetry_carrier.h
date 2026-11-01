// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <google/protobuf/map.h>
#include <opentelemetry/context/propagation/text_map_propagator.h>

namespace vespa_opentelemetry {

/*
 * Class for getting key/value pairs from a protobuf map when setting up a new opentelemetry
 * context associated with an rpc request.
 */
class ProtoTelemetryCarrier : public opentelemetry::context::propagation::TextMapCarrier {
public:
    using Map = google::protobuf::Map<std::string, std::string>;

    explicit ProtoTelemetryCarrier(const Map& map) noexcept;
    ~ProtoTelemetryCarrier();

    bool empty() const noexcept;
    opentelemetry::nostd::string_view Get(opentelemetry::nostd::string_view key) const noexcept override;
    void Set(opentelemetry::nostd::string_view, opentelemetry::nostd::string_view) noexcept override;

private:
    const Map& _map;
};

} // namespace vespa_opentelemetry
