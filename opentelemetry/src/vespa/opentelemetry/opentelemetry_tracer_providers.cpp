// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "opentelemetry_tracer_providers.h"

#include <vespa/config-telemetry.h>
#include <vespa/defaults.h>
#include <vespa/vespalib/component/vtag.h>
#include <vespa/vespalib/net/tls/transport_security_options_reading.h>

#include <opentelemetry/exporters/memory/in_memory_span_exporter_factory.h>
#include <opentelemetry/exporters/ostream/span_exporter_factory.h>
#include <opentelemetry/exporters/otlp/otlp_grpc_exporter_factory.h>
#include <opentelemetry/sdk/trace/batch_span_processor_factory.h>
#include <opentelemetry/sdk/trace/simple_processor_factory.h>
#include <opentelemetry/sdk/trace/tracer_provider_factory.h>
#include <opentelemetry/trace/provider.h>

#include <format>
#include <fstream>
#include <iomanip>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".opentelemetry.opentelemetry_tracer_providers");

namespace vespa_opentelemetry {

const std::string OpenTelemetryTracerProviders::vespa_tls_config_file("VESPA_TLS_CONFIG_FILE");

void OpenTelemetryTracerProviders::cleanup_tracer_provider() {
    std::shared_ptr<opentelemetry::trace::TracerProvider> none;
    opentelemetry::trace::Provider::SetTracerProvider(none);
}

opentelemetry::exporter::otlp::OtlpGrpcExporterOptions OpenTelemetryTracerProviders::setup_otlp_grpc_exporter_options(
    const vespalib::net::tls::TransportSecurityOptions& vespa_tls_config) {
    opentelemetry::exporter::otlp::OtlpGrpcExporterOptions opts{};
    opts.ssl_client_cert_string = vespa_tls_config.cert_chain_pem();
    opts.ssl_client_key_string = vespa_tls_config.private_key_pem();
    opts.ssl_credentials_cacert_as_string = vespa_tls_config.ca_certs_pem();
    opts.use_ssl_credentials = true;
    opts.endpoint = "https://localhost:4317";
    return opts;
}

opentelemetry::sdk::trace::BatchSpanProcessorOptions
OpenTelemetryTracerProviders::setup_batch_span_processor_options(bool test) {
    opentelemetry::sdk::trace::BatchSpanProcessorOptions opts{};
    opts.schedule_delay_millis = std::chrono::milliseconds(test ? 200 : 5000);
    opts.export_timeout = std::chrono::milliseconds(30000);
    opts.max_export_batch_size = 512;
    opts.max_queue_size = 2048;
    return opts;
}

opentelemetry::sdk::resource::Resource OpenTelemetryTracerProviders::make_resource() {
    opentelemetry::sdk::resource::ResourceAttributes resource_attributes{{"service.name", "vespa"},
                                                                         {"service.version", vespalib::VersionTag}};
    return opentelemetry::sdk::resource::Resource::Create(resource_attributes);
}

opentelemetry::sdk::resource::Resource
OpenTelemetryTracerProviders::make_resource(const std::string& service_name, TelemetryConfig& telemetry_config) {
    opentelemetry::sdk::resource::ResourceAttributes resource_attributes;
    for (const auto& resource : telemetry_config.resourceAttribute) {
        resource_attributes.SetAttribute(resource.first, resource.second);
    }
    resource_attributes.SetAttribute("realservice.name", service_name);
    resource_attributes.SetAttribute("realservice.version", vespalib::VersionTag);
    return opentelemetry::sdk::resource::Resource::Create(resource_attributes);
}

void OpenTelemetryTracerProviders::adjust_otlp_grpc_exporter_options(
    opentelemetry::exporter::otlp::OtlpGrpcExporterOptions& opts, TelemetryConfig& cfg) {
    auto          host_name_file_name = vespa::Defaults::underVespaHome(cfg.endpointHostnameFile.c_str());
    std::ifstream host_name_file(host_name_file_name);
    std::string   host_name((std::istreambuf_iterator<char>(host_name_file)), std::istreambuf_iterator<char>());
    opts.endpoint = std::format("https://{}:4317", host_name);
}

void OpenTelemetryTracerProviders::init_grpc_tracer_provider(
    const opentelemetry::exporter::otlp::OtlpGrpcExporterOptions& otlp_grpc_exporter_options,
    const opentelemetry::sdk::trace::BatchSpanProcessorOptions&   batch_span_processor_options,
    const opentelemetry::sdk::resource::Resource&                 resource) {
    auto exporter = opentelemetry::exporter::otlp::OtlpGrpcExporterFactory::Create(otlp_grpc_exporter_options);
    auto processor = opentelemetry::sdk::trace::BatchSpanProcessorFactory::Create(std::move(exporter),
                                                                                  batch_span_processor_options);
    std::shared_ptr<opentelemetry::trace::TracerProvider> provider =
        opentelemetry::sdk::trace::TracerProviderFactory::Create(std::move(processor), resource);
    opentelemetry::trace::Provider::SetTracerProvider(provider);
}

void OpenTelemetryTracerProviders::init_grpc_tracer_provider(const std::string& service_name,
                                                             TelemetryConfig&   telemetry_config) {
    if (!telemetry_config.enabled) {
        return;
    }
    const char* tls_config_file = getenv(vespa_tls_config_file.c_str());
    if (tls_config_file == nullptr) {
        LOG(warning, "environment variable '%s' not set, telemetry is disabled", vespa_tls_config_file.c_str());
        return;
    }
    auto vespa_tls_config = vespalib::net::tls::read_options_from_json_file(tls_config_file);
    if (!vespa_tls_config) {
        std::stringstream ss;
        ss << "bad vespa tls config file " << std::quoted(tls_config_file) << ", telemetry is disabled";
        LOG(warning, "%s", ss.str().c_str());
        return;
    }
    auto otlp_grpc_exporter_options = setup_otlp_grpc_exporter_options(*vespa_tls_config);
    adjust_otlp_grpc_exporter_options(otlp_grpc_exporter_options, telemetry_config);
    auto batch_span_processor_options = setup_batch_span_processor_options(false);
    auto resource = make_resource(service_name, telemetry_config);
    init_grpc_tracer_provider(otlp_grpc_exporter_options, batch_span_processor_options, resource);
}

std::shared_ptr<opentelemetry::exporter::memory::InMemorySpanData>
OpenTelemetryTracerProviders::init_memory_tracer_provider() {
    std::shared_ptr<opentelemetry::exporter::memory::InMemorySpanData> data;
    auto exporter = opentelemetry::exporter::memory::InMemorySpanExporterFactory::Create(data);
    auto processor = opentelemetry::sdk::trace::SimpleSpanProcessorFactory::Create(std::move(exporter));
    auto resource = make_resource();
    std::shared_ptr<opentelemetry::trace::TracerProvider> provider =
        opentelemetry::sdk::trace::TracerProviderFactory::Create(std::move(processor), resource);
    opentelemetry::trace::Provider::SetTracerProvider(provider);
    return data;
}

void OpenTelemetryTracerProviders::init_ostream_tracer_provider(std::ostream& os) {
    auto exporter = opentelemetry::exporter::trace::OStreamSpanExporterFactory::Create(os);
    auto processor = opentelemetry::sdk::trace::SimpleSpanProcessorFactory::Create(std::move(exporter));
    auto resource = make_resource();
    std::shared_ptr<opentelemetry::trace::TracerProvider> provider =
        opentelemetry::sdk::trace::TracerProviderFactory::Create(std::move(processor), resource);
    opentelemetry::trace::Provider::SetTracerProvider(provider);
}

} // namespace vespa_opentelemetry
