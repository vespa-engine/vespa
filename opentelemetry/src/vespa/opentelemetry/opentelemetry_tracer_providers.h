// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/net/tls/transport_security_options.h>

#include <opentelemetry/exporters/memory/in_memory_span_data.h>
#include <opentelemetry/exporters/otlp/otlp_grpc_exporter_options.h>
#include <opentelemetry/sdk/resource/resource.h>
#include <opentelemetry/sdk/trace/batch_span_processor_options.h>

#include <memory>

namespace vespa_opentelemetry {

/*
 * Functions for setting up open telemetry trace providers to an ostream, to a memory buffer or to grpc.
 */
class OpenTelemetryTracerProviders {
public:
    static const std::string vespa_tls_config_file;

    static void cleanup_tracer_provider();

    static opentelemetry::exporter::otlp::OtlpGrpcExporterOptions
    setup_otlp_grpc_exporter_options(const vespalib::net::tls::TransportSecurityOptions& vespa_tls_config);
    static opentelemetry::sdk::trace::BatchSpanProcessorOptions setup_batch_span_processor_options(bool test);
    static opentelemetry::sdk::resource::Resource make_resource();
    static void init_grpc_tracer_provider(
        const opentelemetry::exporter::otlp::OtlpGrpcExporterOptions& otlp_grpc_exporter_options,
        const opentelemetry::sdk::trace::BatchSpanProcessorOptions&   batch_span_processor_options,
        const opentelemetry::sdk::resource::Resource&                 resource);

    static std::shared_ptr<opentelemetry::exporter::memory::InMemorySpanData> init_memory_tracer_provider();

    static void init_ostream_tracer_provider(std::ostream& os);
};

} // namespace vespa_opentelemetry
