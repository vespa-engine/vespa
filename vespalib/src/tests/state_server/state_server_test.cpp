// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/net/connection_auth_context.h>
#include <vespa/vespalib/net/http/state_server.h>
#include <vespa/vespalib/net/http/simple_health_producer.h>
#include <vespa/vespalib/net/http/simple_metrics_producer.h>
#include <vespa/vespalib/net/http/simple_component_config_producer.h>
#include <vespa/vespalib/net/http/state_explorer.h>
#include <vespa/vespalib/net/http/slime_explorer.h>
#include <vespa/vespalib/net/http/generic_state_handler.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/host_name.h>
#include <vespa/vespalib/process/process.h>
#include <sys/stat.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/testkit/test_master.hpp>

using namespace vespalib;

//-----------------------------------------------------------------------------

vespalib::string root_path = "/state/v1/";
vespalib::string short_root_path = "/state/v1";
vespalib::string metrics_path = "/state/v1/metrics";
vespalib::string health_path = "/state/v1/health";
vespalib::string config_path = "/state/v1/config";

vespalib::string total_metrics_path = "/metrics/total";

vespalib::string unknown_path = "/this/path/is/not/known";
vespalib::string unknown_state_path = "/state/v1/this/path/is/not/known";
vespalib::string my_path = "/my/path";

vespalib::string host_tag = "HOST";
std::map<vespalib::string,vespalib::string> empty_params;

//-----------------------------------------------------------------------------

vespalib::string run_cmd(const vespalib::string &cmd) {
    vespalib::string out;
    ASSERT_TRUE(Process::run(cmd.c_str(), out));
    return out;
}

vespalib::string getPage(int port, const vespalib::string &path, const vespalib::string &extra_params = "") {
    return run_cmd(make_string("curl -s %s 'http://localhost:%d%s'", extra_params.c_str(), port, path.c_str()));
}

vespalib::string getFull(int port, const vespalib::string &path) { return getPage(port, path, "-D -"); }

std::pair<vespalib::string, vespalib::string>
get_body_and_content_type(const JsonGetHandler &handler,
                          const vespalib::string &host,
                          const vespalib::string &path,
                          const std::map<vespalib::string,vespalib::string> &params)
{
    net::ConnectionAuthContext dummy_ctx(net::tls::PeerCredentials(), net::tls::CapabilitySet::all());
    auto res = handler.get(host, path, params, dummy_ctx);
    if (res.ok()) {
        return {res.payload(), res.content_type()};
    }
    return {};
}

vespalib::string get_json(const JsonGetHandler &handler,
                          const vespalib::string &host,
                          const vespalib::string &path,
                          const std::map<vespalib::string,vespalib::string> &params)
{
    return get_body_and_content_type(handler, host, path, params).first;
}

//-----------------------------------------------------------------------------

struct DummyHandler : JsonGetHandler {
    vespalib::string result;
    DummyHandler(const vespalib::string &result_in) : result(result_in) {}
    Response get(const vespalib::string &, const vespalib::string &,
                 const std::map<vespalib::string,vespalib::string> &,
                 const net::ConnectionAuthContext &) const override
    {
        if (!result.empty()) {
            return Response::make_ok_with_json(result);
        } else {
            return Response::make_not_found();
        }
    }
};

//-----------------------------------------------------------------------------

TEST_F("require that unknown url returns 404 response", HttpServer(0)) {
    std::string expect("HTTP/1.1 404 Not Found\r\n"
                       "Connection: close\r\n"
                       "\r\n");
    std::string actual = getFull(f1.port(), unknown_path);
    EXPECT_EQUAL(expect, actual);
}

TEST_FF("require that handler can return a 404 response", DummyHandler(""), HttpServer(0)) {
    auto token = f2.repo().bind(my_path, f1);
    std::string expect("HTTP/1.1 404 Not Found\r\n"
                       "Connection: close\r\n"
                       "\r\n");
    std::string actual = getFull(f2.port(), my_path);
    EXPECT_EQUAL(expect, actual);
}

TEST_FF("require that non-empty known url returns expected headers", DummyHandler("[123]"), HttpServer(0)) {
    auto token = f2.repo().bind(my_path, f1);
    vespalib::string expect("HTTP/1.1 200 OK\r\n"
                            "Connection: close\r\n"
                            "Content-Type: application/json\r\n"
                            "Content-Length: 5\r\n"
                            "X-XSS-Protection: 1; mode=block\r\n"
                            "X-Frame-Options: DENY\r\n"
                            "Content-Security-Policy: default-src 'none'; frame-ancestors 'none'\r\n"
                            "X-Content-Type-Options: nosniff\r\n"
                            "Cache-Control: no-store\r\n"
                            "Pragma: no-cache\r\n"
                            "\r\n"
                            "[123]");
    std::string actual = getFull(f2.port(), my_path);
    EXPECT_EQUAL(expect, actual);
}

TEST_FFFF("require that handler is selected based on longest matching url prefix",
          DummyHandler("[1]"), DummyHandler("[2]"), DummyHandler("[3]"),
          HttpServer(0))
{
    auto token2 = f4.repo().bind("/foo/bar", f2);
    auto token1 = f4.repo().bind("/foo", f1);
    auto token3 = f4.repo().bind("/foo/bar/baz", f3);
    int port = f4.port();
    EXPECT_EQUAL("", getPage(port, "/fox"));
    EXPECT_EQUAL("[1]", getPage(port, "/foo"));
    EXPECT_EQUAL("[1]", getPage(port, "/foo/fox"));
    EXPECT_EQUAL("[2]", getPage(port, "/foo/bar"));
    EXPECT_EQUAL("[2]", getPage(port, "/foo/bar/fox"));
    EXPECT_EQUAL("[3]", getPage(port, "/foo/bar/baz"));
    EXPECT_EQUAL("[3]", getPage(port, "/foo/bar/baz/fox"));
}

struct EchoHost : JsonGetHandler {
    ~EchoHost() override;
    Response get(const vespalib::string &host, const vespalib::string &,
                 const std::map<vespalib::string,vespalib::string> &,
                 const net::ConnectionAuthContext &) const override
    {
        return Response::make_ok_with_json("[\"" + host + "\"]");
    }
};

EchoHost::~EchoHost() = default;

TEST_FF("require that host is passed correctly", EchoHost(), HttpServer(0)) {
    auto token = f2.repo().bind(my_path, f1);
    EXPECT_EQUAL(make_string("%s:%d", HostName::get().c_str(), f2.port()), f2.host());
    vespalib::string default_result = make_string("[\"%s\"]", f2.host().c_str());
    vespalib::string localhost_result = make_string("[\"%s:%d\"]", "localhost", f2.port());
    vespalib::string silly_result = "[\"sillyserver\"]";
    EXPECT_EQUAL(localhost_result, run_cmd(make_string("curl -s http://localhost:%d/my/path", f2.port())));
    EXPECT_EQUAL(silly_result, run_cmd(make_string("curl -s http://localhost:%d/my/path -H \"Host: sillyserver\"", f2.port())));
    EXPECT_EQUAL(default_result, run_cmd(make_string("curl -s http://localhost:%d/my/path -H \"Host:\"", f2.port())));
}

struct SamplingHandler : JsonGetHandler {
    mutable std::mutex my_lock;
    mutable vespalib::string my_host;
    mutable vespalib::string my_path;
    mutable std::map<vespalib::string,vespalib::string> my_params;
    ~SamplingHandler() override;
    Response get(const vespalib::string &host, const vespalib::string &path,
                 const std::map<vespalib::string,vespalib::string> &params,
                 const net::ConnectionAuthContext &) const override
    {
        {
            auto guard = std::lock_guard(my_lock);
            my_host = host;
            my_path = path;
            my_params = params;
        }
        return Response::make_ok_with_json("[]");
    }
};

SamplingHandler::~SamplingHandler() = default;

TEST_FF("require that request parameters can be inspected", SamplingHandler(), HttpServer(0))
{
    auto token = f2.repo().bind("/foo", f1);
    EXPECT_EQUAL("[]", getPage(f2.port(), "/foo?a=b&x=y&z"));
    {
        auto guard = std::lock_guard(f1.my_lock);
        EXPECT_EQUAL(f1.my_path, "/foo");
        EXPECT_EQUAL(f1.my_params.size(), 3u);
        EXPECT_EQUAL(f1.my_params["a"], "b");
        EXPECT_EQUAL(f1.my_params["x"], "y");
        EXPECT_EQUAL(f1.my_params["z"], "");
        EXPECT_EQUAL(f1.my_params.size(), 3u); // "z" was present
    }
}

TEST_FF("require that request path is dequoted", SamplingHandler(), HttpServer(0))
{
    auto token = f2.repo().bind("/[foo]", f1);
    EXPECT_EQUAL("[]", getPage(f2.port(), "/%5bfoo%5D"));
    {
        auto guard = std::lock_guard(f1.my_lock);
        EXPECT_EQUAL(f1.my_path, "/[foo]");
        EXPECT_EQUAL(f1.my_params.size(), 0u);
    }
}

//-----------------------------------------------------------------------------

TEST_FFFF("require that the state server wires the appropriate url prefixes",
          SimpleHealthProducer(), SimpleMetricsProducer(), SimpleComponentConfigProducer(),
          StateServer(0, f1, f2, f3))
{
    f2.setTotalMetrics("{}", MetricsProducer::ExpositionFormat::JSON); // avoid empty result
    int port = f4.getListenPort();
    EXPECT_TRUE(getFull(port, short_root_path).find("HTTP/1.1 200 OK") == 0);
    EXPECT_TRUE(getFull(port, total_metrics_path).find("HTTP/1.1 200 OK") == 0);
    EXPECT_TRUE(getFull(port, unknown_path).find("HTTP/1.1 404 Not Found") == 0);
}

TEST_FFFF("require that the state server exposes the state api handler repo",
          SimpleHealthProducer(), SimpleMetricsProducer(), SimpleComponentConfigProducer(),
          StateServer(0, f1, f2, f3))
{
    int port = f4.getListenPort();
    vespalib::string page1 = getPage(port, root_path);
    auto token = f4.repo().add_root_resource("state/v1/custom");
    vespalib::string page2 = getPage(port, root_path);
    EXPECT_NOT_EQUAL(page1, page2);
    token.reset();
    vespalib::string page3 = getPage(port, root_path);
    EXPECT_EQUAL(page3, page1);
}

//-----------------------------------------------------------------------------

TEST_FFFF("require that json handlers can be removed from repo",
          DummyHandler("[1]"), DummyHandler("[2]"), DummyHandler("[3]"),
          JsonHandlerRepo())
{
    auto token1 = f4.bind("/foo", f1);
    auto token2 = f4.bind("/foo/bar", f2);
    auto token3 = f4.bind("/foo/bar/baz", f3);
    std::map<vespalib::string,vespalib::string> params;
    EXPECT_EQUAL("[1]", get_json(f4, "", "/foo", params));
    EXPECT_EQUAL("[2]", get_json(f4, "", "/foo/bar", params));
    EXPECT_EQUAL("[3]", get_json(f4, "", "/foo/bar/baz", params));
    token2.reset();
    EXPECT_EQUAL("[1]", get_json(f4, "", "/foo", params));
    EXPECT_EQUAL("[1]", get_json(f4, "", "/foo/bar", params));
    EXPECT_EQUAL("[3]", get_json(f4, "", "/foo/bar/baz", params));
}

TEST_FFFF("require that json handlers can be shadowed",
          DummyHandler("[1]"), DummyHandler("[2]"), DummyHandler("[3]"),
          JsonHandlerRepo())
{
    auto token1 = f4.bind("/foo", f1);
    auto token2 = f4.bind("/foo/bar", f2);
    std::map<vespalib::string,vespalib::string> params;
    EXPECT_EQUAL("[1]", get_json(f4, "", "/foo", params));
    EXPECT_EQUAL("[2]", get_json(f4, "", "/foo/bar", params));
    auto token3 = f4.bind("/foo/bar", f3);
    EXPECT_EQUAL("[3]", get_json(f4, "", "/foo/bar", params));
    token3.reset();
    EXPECT_EQUAL("[2]", get_json(f4, "", "/foo/bar", params));
}

TEST_F("require that root resources can be tracked", JsonHandlerRepo())
{
    EXPECT_TRUE(std::vector<vespalib::string>({}) == f1.get_root_resources());
    auto token1 = f1.add_root_resource("/health");
    EXPECT_TRUE(std::vector<vespalib::string>({"/health"}) == f1.get_root_resources());
    auto token2 = f1.add_root_resource("/config");
    EXPECT_TRUE(std::vector<vespalib::string>({"/health", "/config"}) == f1.get_root_resources());
    auto token3 = f1.add_root_resource("/custom/foo");
    EXPECT_TRUE(std::vector<vespalib::string>({"/health", "/config", "/custom/foo"}) == f1.get_root_resources());    
    token2.reset();
    EXPECT_TRUE(std::vector<vespalib::string>({"/health", "/custom/foo"}) == f1.get_root_resources());
}

//-----------------------------------------------------------------------------

TEST_FFFF("require that state api responds to the expected paths",
          SimpleHealthProducer(), SimpleMetricsProducer(), SimpleComponentConfigProducer(),
          StateApi(f1, f2, f3))
{
    f2.setTotalMetrics("{}", MetricsProducer::ExpositionFormat::JSON); // avoid empty result
    EXPECT_TRUE(!get_json(f4, host_tag, short_root_path, empty_params).empty());
    EXPECT_TRUE(!get_json(f4, host_tag, root_path, empty_params).empty());
    EXPECT_TRUE(!get_json(f4, host_tag, health_path, empty_params).empty());
    EXPECT_TRUE(!get_json(f4, host_tag, metrics_path, empty_params).empty());
    EXPECT_TRUE(!get_json(f4, host_tag, config_path, empty_params).empty());
    EXPECT_TRUE(!get_json(f4, host_tag, total_metrics_path, empty_params).empty());
    EXPECT_TRUE(get_json(f4, host_tag, unknown_path, empty_params).empty());
    EXPECT_TRUE(get_json(f4, host_tag, unknown_state_path, empty_params).empty());
}

TEST_FFFF("require that top-level urls are generated correctly",
          SimpleHealthProducer(), SimpleMetricsProducer(), SimpleComponentConfigProducer(),
          StateApi(f1, f2, f3))
{
    EXPECT_EQUAL("{\"resources\":["
                 "{\"url\":\"http://HOST/state/v1/health\"},"
                 "{\"url\":\"http://HOST/state/v1/metrics\"},"
                 "{\"url\":\"http://HOST/state/v1/config\"}]}",
                 get_json(f4, host_tag, root_path, empty_params));
    EXPECT_EQUAL(get_json(f4, host_tag, root_path, empty_params),
                 get_json(f4, host_tag, short_root_path, empty_params));
}

TEST_FFFF("require that top-level resource list can be extended",
          SimpleHealthProducer(), SimpleMetricsProducer(), SimpleComponentConfigProducer(),
          StateApi(f1, f2, f3))
{
    auto token = f4.repo().add_root_resource("/state/v1/custom");
    EXPECT_EQUAL("{\"resources\":["
                 "{\"url\":\"http://HOST/state/v1/health\"},"
                 "{\"url\":\"http://HOST/state/v1/metrics\"},"
                 "{\"url\":\"http://HOST/state/v1/config\"},"
                 "{\"url\":\"http://HOST/state/v1/custom\"}]}",
                 get_json(f4, host_tag, root_path, empty_params));
}

TEST_FFFF("require that health resource works as expected",
          SimpleHealthProducer(), SimpleMetricsProducer(), SimpleComponentConfigProducer(),
          StateApi(f1, f2, f3))
{
    EXPECT_EQUAL("{\"status\":{\"code\":\"up\"}}",
                 get_json(f4, host_tag, health_path, empty_params));
    f1.setFailed("FAIL MSG");
    EXPECT_EQUAL("{\"status\":{\"code\":\"down\",\"message\":\"FAIL MSG\"}}",
                 get_json(f4, host_tag, health_path, empty_params));
}

TEST_FFFF("require that metrics resource works as expected",
          SimpleHealthProducer(), SimpleMetricsProducer(), SimpleComponentConfigProducer(),
          StateApi(f1, f2, f3))
{
    EXPECT_EQUAL("{\"status\":{\"code\":\"up\"}}",
                 get_json(f4, host_tag, metrics_path, empty_params));
    f1.setFailed("FAIL MSG");
    EXPECT_EQUAL("{\"status\":{\"code\":\"down\",\"message\":\"FAIL MSG\"}}",
                 get_json(f4, host_tag, metrics_path, empty_params));
    f1.setOk();
    f2.setMetrics(R"({"foo":"bar"})", MetricsProducer::ExpositionFormat::JSON);
    f2.setMetrics(R"(cool_stuff{hello="world"} 1 23456)", MetricsProducer::ExpositionFormat::Prometheus);

    auto result = get_body_and_content_type(f4, host_tag, metrics_path, empty_params);
    EXPECT_EQUAL(R"({"status":{"code":"up"},"metrics":{"foo":"bar"}})", result.first);
    EXPECT_EQUAL("application/json", result.second);

    result = get_body_and_content_type(f4, host_tag, metrics_path, {{"format", "json"}}); // Explicit JSON
    EXPECT_EQUAL(R"({"status":{"code":"up"},"metrics":{"foo":"bar"}})", result.first);
    EXPECT_EQUAL("application/json", result.second);

    result = get_body_and_content_type(f4, host_tag, metrics_path, {{"format", "prometheus"}}); // Explicit Prometheus
    EXPECT_EQUAL(R"(cool_stuff{hello="world"} 1 23456)", result.first);
    EXPECT_EQUAL("text/plain; version=0.0.4", result.second);
}

TEST_FFFF("require that config resource works as expected",
          SimpleHealthProducer(), SimpleMetricsProducer(), SimpleComponentConfigProducer(),
          StateApi(f1, f2, f3))
{
    EXPECT_EQUAL("{\"config\":{}}",
                 get_json(f4, host_tag, config_path, empty_params));
    f3.addConfig(SimpleComponentConfigProducer::Config("foo", 3));
    EXPECT_EQUAL("{\"config\":{\"generation\":3,\"foo\":{\"generation\":3}}}",
                 get_json(f4, host_tag, config_path, empty_params));
    f3.addConfig(SimpleComponentConfigProducer::Config("foo", 4));
    f3.addConfig(SimpleComponentConfigProducer::Config("bar", 4, "error"));
    EXPECT_EQUAL("{\"config\":{\"generation\":4,\"bar\":{\"generation\":4,\"message\":\"error\"},\"foo\":{\"generation\":4}}}",
                 get_json(f4, host_tag, config_path, empty_params));
    f3.removeConfig("bar");
    EXPECT_EQUAL("{\"config\":{\"generation\":4,\"foo\":{\"generation\":4}}}",
                 get_json(f4, host_tag, config_path, empty_params));
}

TEST_FFFF("require that state api also can return total metric",
          SimpleHealthProducer(), SimpleMetricsProducer(), SimpleComponentConfigProducer(),
          StateApi(f1, f2, f3))
{
    f2.setTotalMetrics(R"({"foo":"bar"})", MetricsProducer::ExpositionFormat::JSON);
    f2.setTotalMetrics(R"(cool_stuff{hello="world"} 1 23456)", MetricsProducer::ExpositionFormat::Prometheus);
    EXPECT_EQUAL(R"({"foo":"bar"})",
                 get_json(f4, host_tag, total_metrics_path, empty_params));
    EXPECT_EQUAL(R"(cool_stuff{hello="world"} 1 23456)",
                 get_json(f4, host_tag, total_metrics_path, {{"format", "prometheus"}}));
}

TEST_FFFFF("require that custom handlers can be added to the state server",
          SimpleHealthProducer(), SimpleMetricsProducer(), SimpleComponentConfigProducer(),
          StateApi(f1, f2, f3), DummyHandler("[123]"))
{
    EXPECT_EQUAL("", get_json(f4, host_tag, my_path, empty_params));
    auto token = f4.repo().bind(my_path, f5);
    EXPECT_EQUAL("[123]", get_json(f4, host_tag, my_path, empty_params));
    token.reset();
    EXPECT_EQUAL("", get_json(f4, host_tag, my_path, empty_params));
}

struct EchoConsumer : MetricsProducer {
    static constexpr const char* to_string(ExpositionFormat format) noexcept {
        switch (format) {
        case ExpositionFormat::JSON: return "JSON";
        case ExpositionFormat::Prometheus: return "Prometheus";
        }
        abort();
    }

    static vespalib::string stringify_params(const vespalib::string &consumer, ExpositionFormat format) {
        // Not semantically meaningful output if format == Prometheus, but doesn't really matter here.
        return vespalib::make_string(R"(["%s", "%s"])", to_string(format), consumer.c_str());
    }

    ~EchoConsumer() override;
    vespalib::string getMetrics(const vespalib::string &consumer, ExpositionFormat format) override {
        return stringify_params(consumer, format);
    }
    vespalib::string getTotalMetrics(const vespalib::string &consumer, ExpositionFormat format) override {
        return stringify_params(consumer, format);
    }
};

EchoConsumer::~EchoConsumer() = default;

TEST_FFFF("require that empty v1 metrics consumer defaults to 'statereporter'",
          SimpleHealthProducer(), EchoConsumer(), SimpleComponentConfigProducer(),
          StateApi(f1, f2, f3))
{
    EXPECT_EQUAL(R"({"status":{"code":"up"},"metrics":["JSON", "statereporter"]})",
                 get_json(f4, host_tag, metrics_path, empty_params));
    EXPECT_EQUAL(R"(["Prometheus", "statereporter"])",
                 get_json(f4, host_tag, metrics_path, {{"format", "prometheus"}}));
}

TEST_FFFF("require that empty total metrics consumer defaults to the empty string",
          SimpleHealthProducer(), EchoConsumer(), SimpleComponentConfigProducer(),
          StateApi(f1, f2, f3))
{
    EXPECT_EQUAL(R"(["JSON", ""])", get_json(f4, host_tag, total_metrics_path, empty_params));
}

TEST_FFFF("require that metrics consumer is passed correctly",
          SimpleHealthProducer(), EchoConsumer(), SimpleComponentConfigProducer(),
          StateApi(f1, f2, f3))
{
    std::map<vespalib::string,vespalib::string> my_params;
    my_params["consumer"] = "ME";
    EXPECT_EQUAL(R"({"status":{"code":"up"},"metrics":["JSON", "ME"]})", get_json(f4, host_tag, metrics_path, my_params));
    EXPECT_EQUAL(R"(["JSON", "ME"])", get_json(f4, host_tag, total_metrics_path, my_params));
    my_params["format"] = "prometheus";
    EXPECT_EQUAL(R"(["Prometheus", "ME"])", get_json(f4, host_tag, total_metrics_path, my_params));
}

void check_json(const vespalib::string &expect_json, const vespalib::string &actual_json) {
    Slime expect_slime;
    Slime actual_slime;
    EXPECT_TRUE(slime::JsonFormat::decode(expect_json, expect_slime) > 0);
    EXPECT_TRUE(slime::JsonFormat::decode(actual_json, actual_slime) > 0);
    EXPECT_EQUAL(expect_slime, actual_slime);
}

TEST("require that generic state can be explored") {
    vespalib::string json_model =
        "{"
        "  foo: 'bar',"
        "  cnt: 123,"
        "  engine: {"
        "    up: 'yes',"
        "    stats: {"
        "      latency: 5,"
        "      qps: 100"
        "    }"
        "  },"
        "  list: {"
        "    one: {"
        "      size: {"
        "        value: 1"
        "      }"
        "    },"
        "    two: {"
        "      size: 2"
        "    }"
        "  }"
        "}";
    vespalib::string json_root =
        "{"
        "  full: true,"
        "  foo: 'bar',"
        "  cnt: 123,"
        "  engine: {"
        "    up: 'yes',"
        "    url: 'http://HOST/state/v1/engine'"
        "  },"
        "  list: {"
        "    one: {"
        "      size: {"
        "        value: 1,"
        "        url: 'http://HOST/state/v1/list/one/size'"
        "      }"
        "    },"
        "    two: {"
        "      size: 2,"
        "      url: 'http://HOST/state/v1/list/two'"
        "    }"
        "  }"
        "}";
    vespalib::string json_engine =
        "{"
        "  full: true,"
        "  up: 'yes',"
        "  stats: {"
        "    latency: 5,"
        "    qps: 100,"
        "    url: 'http://HOST/state/v1/engine/stats'"
        "  }"
        "}";
    vespalib::string json_engine_stats =
        "{"
        "  full: true,"
        "  latency: 5,"
        "  qps: 100"
        "}";
    vespalib::string json_list =
        "{"
        "  one: {"
        "    size: {"
        "      value: 1,"
        "      url: 'http://HOST/state/v1/list/one/size'"
        "    }"
        "  },"
        "  two: {"
        "    size: 2,"
        "    url: 'http://HOST/state/v1/list/two'"
        "  }"
        "}";
    vespalib::string json_list_one =
        "{"
        "  size: {"
        "    value: 1,"
        "    url: 'http://HOST/state/v1/list/one/size'"
        "  }"
        "}";
    vespalib::string json_list_one_size = "{ full: true, value: 1 }";
    vespalib::string json_list_two = "{ full: true, size: 2 }";
    //-------------------------------------------------------------------------
    Slime slime_state;
    EXPECT_TRUE(slime::JsonFormat::decode(json_model, slime_state) > 0);
    SlimeExplorer slime_explorer(slime_state.get());
    GenericStateHandler state_handler(short_root_path, slime_explorer);
    EXPECT_EQUAL("", get_json(state_handler, host_tag, unknown_path, empty_params));
    EXPECT_EQUAL("", get_json(state_handler, host_tag, unknown_state_path, empty_params));
    check_json(json_root, get_json(state_handler, host_tag, root_path, empty_params));
    check_json(json_engine, get_json(state_handler, host_tag, root_path + "engine", empty_params));
    check_json(json_engine_stats, get_json(state_handler, host_tag, root_path + "engine/stats", empty_params));
    check_json(json_list, get_json(state_handler, host_tag, root_path + "list", empty_params));
    check_json(json_list_one, get_json(state_handler, host_tag, root_path + "list/one", empty_params));
    check_json(json_list_one_size, get_json(state_handler, host_tag, root_path + "list/one/size", empty_params));
    check_json(json_list_two, get_json(state_handler, host_tag, root_path + "list/two", empty_params));
}

TEST_MAIN() {
    mkdir("var", S_IRWXU);
    mkdir("var/run", S_IRWXU);
    TEST_RUN_ALL();
    rmdir("var/run");
    rmdir("var");
}
