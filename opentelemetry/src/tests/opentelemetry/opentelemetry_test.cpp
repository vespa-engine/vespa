// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/opentelemetry/opentelemetry_tracer_providers.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/net/tls/transport_security_options_reading.h>
#include <vespa/vespalib/process/process.h>

#include <opentelemetry/trace/provider.h>

#include <filesystem>
#include <fstream>
#include <iomanip>
#include <iostream>
#include <sstream>

using vespalib::Process;
using vespalib::Slime;
using vespalib::net::tls::TransportSecurityOptions;

namespace vespa_opentelemetry {

class OpenTelemetryTest : public ::testing::Test {
protected:
    OpenTelemetryTest();
    ~OpenTelemetryTest() override;
    void TearDown() override;
    void emit_hello_world_span();
    void write_otelcol_config(const std::string& tls_config_file_name);
    static std::string hello_world;
    static std::string otelcol_binary;
};

std::string OpenTelemetryTest::hello_world("Hello world");
std::string OpenTelemetryTest::otelcol_binary("/opt/vespa-deps/bin/vespa-otelcol");

OpenTelemetryTest::OpenTelemetryTest() {
}

OpenTelemetryTest::~OpenTelemetryTest() {
}

void OpenTelemetryTest::TearDown() {
    OpenTelemetryTracerProviders::cleanup_tracer_provider();
}

void OpenTelemetryTest::emit_hello_world_span() {
    auto tracer = ::opentelemetry::trace::Provider::GetTracerProvider()->GetTracer("opentelemetry-test");
    auto span = tracer->StartSpan(hello_world);
    span->End();
}

void OpenTelemetryTest::write_otelcol_config(const std::string& tls_config_file_name) {
    std::ifstream tls_config_file(tls_config_file_name);
    std::string   tls_config((std::istreambuf_iterator<char>(tls_config_file)), std::istreambuf_iterator<char>());
    Slime         root;
    auto          parsed = vespalib::slime::JsonFormat::decode(tls_config, root);
    ASSERT_NE(0, parsed) << "Provided TLS config file is not valid JSON";
    auto& files = root["files"];
    auto  file = [&files](const std::string& key) { return files[key].asString().make_string(); };

    std::ofstream otelcol_config("otelcol-config.yaml");
    otelcol_config << "receivers:\n"
                      "  otlp:\n"
                      "    protocols:\n"
                      "      grpc:\n"
                      "        endpoint: 127.0.0.1:4317\n"
                      "        tls:\n"
                      "          cert_file: "
                   << file("certificates")
                   << "\n"
                      "          key_file: "
                   << file("private-key")
                   << "\n"
                      "          client_ca_file: "
                   << file("ca-certificates")
                   << "\n"
                      "          min_version: \"1.2\"\n"
                      "          max_version: \"1.3\"\n"
                      "\n"
                      "processors:\n"
                      "  batch:\n"
                      "    timeout: 100ms\n"
                      "    send_batch_size: 8192\n"
                      "    send_batch_max_size: 0\n"
                      "\n"
                      "exporters:\n"
                      "  file:\n"
                      "    path: ./traces.json\n"
                      "\n"
                      "service:\n"
                      "  pipelines:\n"
                      "    traces:\n"
                      "      receivers: [otlp]\n"
                      "      processors: [batch]\n"
                      "      exporters: [file]\n"
                      "  \n"
                      "  telemetry:\n"
                      "    logs:\n"
                      "      level: \"info\"\n"
                      "\n";
    otelcol_config.close();
}

TEST_F(OpenTelemetryTest, hello_world_to_stream) {
    std::stringstream ss;
    OpenTelemetryTracerProviders::init_ostream_tracer_provider(ss);
    emit_hello_world_span();
    std::cout << ss.str() << std::flush;
    EXPECT_NE(std::string::npos, ss.str().find(hello_world));
}

TEST_F(OpenTelemetryTest, hello_world_to_memory) {
    auto data = OpenTelemetryTracerProviders::init_memory_tracer_provider();
    emit_hello_world_span();
    auto spans = data->GetSpans();
    ASSERT_EQ(1, spans.size());
    EXPECT_EQ(hello_world, spans[0]->GetName());
}

TEST_F(OpenTelemetryTest, hello_world_to_grpc) {
    const char* tls_config_file = getenv(OpenTelemetryTracerProviders::vespa_tls_config_file.c_str());
    if (tls_config_file == nullptr) {
        GTEST_SKIP() << "vespa tls env not configured";
        return;
    }
    if (!std::filesystem::exists(otelcol_binary)) {
        GTEST_SKIP() << "Missing otelcol binary " << std::quoted(otelcol_binary);
        return;
    }
    auto vespa_tls_config = vespalib::net::tls::read_options_from_json_file(tls_config_file);
    ASSERT_TRUE(vespa_tls_config) << "bad vespa tls config file " << std::quoted(tls_config_file);
    auto otlp_grpc_exporter_options =
        OpenTelemetryTracerProviders::setup_otlp_grpc_exporter_options(*vespa_tls_config);
    auto batch_span_processor_options = OpenTelemetryTracerProviders::setup_batch_span_processor_options(true);
    OpenTelemetryTracerProviders::init_grpc_tracer_provider(otlp_grpc_exporter_options, batch_span_processor_options,
                                                            OpenTelemetryTracerProviders::make_resource());

    std::string traces_json("traces.json");
    std::filesystem::remove(traces_json);
    ASSERT_NO_FATAL_FAILURE(write_otelcol_config(tls_config_file));
    /*
     * 2 second margin for vespa-otelcol startup before emitting trace
     * 1 seconds delay due to batch processor.
     * 2 seconds margin for trace to reach vespa-otelcol
     * 2 + 1 + 2 = 5 seconds sleep.
     */
    std::thread thread([]() {
        system("(vespa-otelcol --config otelcol-config.yaml & otelcol_pid=$!; sleep 5; kill $otelcol_pid)");
    });
    // Wait for vespa-otelcol to start
    std::this_thread::sleep_for(2s);
    std::cerr << "Emitting span" << std::endl;
    emit_hello_world_span();
    std::cerr << "Waiting for vespa-otelcol" << std::endl;
    thread.join();
    std::ifstream traces_file(traces_json);
    std::string   traces((std::istreambuf_iterator<char>(traces_file)), std::istreambuf_iterator<char>());
    EXPECT_NE(std::string::npos, traces.find(hello_world));
    std::filesystem::remove(traces_json);
}

} // namespace vespa_opentelemetry

GTEST_MAIN_RUN_ALL_TESTS()
