// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/host_name.h>
#include <vespa/vespalib/net/state_server.h>
#include <vespa/vespalib/net/simple_health_producer.h>
#include <vespa/vespalib/net/simple_metrics_producer.h>
#include <vespa/vespalib/net/simple_component_config_producer.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/slaveproc.h>
#include <vespa/vespalib/net/state_explorer.h>
#include <vespa/vespalib/net/slime_explorer.h>
#include <vespa/vespalib/net/generic_state_handler.h>

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
    std::string out;
    ASSERT_TRUE(SlaveProc::run(cmd.c_str(), out));
    return out;
}

vespalib::string getPage(int port, const vespalib::string &path, const vespalib::string &extra_params = "") {
    vespalib::string result = run_cmd(make_string("curl -s %s http://localhost:%d%s", extra_params.c_str(), port, path.c_str()));
    vespalib::string chunked_result = run_cmd(make_string("curl -H transfer-encoding:chunked -s %s http://localhost:%d%s", extra_params.c_str(), port, path.c_str()));
    ASSERT_EQUAL(result, chunked_result);
    return result;
}

vespalib::string getFull(int port, const vespalib::string &path) { return getPage(port, path, "-D -"); }

//-----------------------------------------------------------------------------

struct DummyHandler : JsonGetHandler {
    vespalib::string result;
    DummyHandler(const vespalib::string &result_in) : result(result_in) {}
    vespalib::string get(const vespalib::string &, const vespalib::string &,
                         const std::map<vespalib::string,vespalib::string> &) const override
    {
        return result;
    }
};

//-----------------------------------------------------------------------------

TEST_F("require that unknown url returns 404 response", HttpServer(0)) {
    f1.start();
    std::string expect("HTTP/1.1 404 Not Found\r\n"
                       "Connection: close\r\n"
                       "\r\n");
    std::string actual = getFull(f1.port(), unknown_path);
    EXPECT_EQUAL(expect, actual);
}

TEST_FF("require that empty known url returns 404 response", DummyHandler(""), HttpServer(0)) {
    auto token = f2.repo().bind(my_path, f1);
    f2.start();
    std::string expect("HTTP/1.1 404 Not Found\r\n"
                       "Connection: close\r\n"
                       "\r\n");
    std::string actual = getFull(f2.port(), my_path);
    EXPECT_EQUAL(expect, actual);
}

TEST_FF("require that non-empty known url returns expected headers", DummyHandler("[123]"), HttpServer(0)) {
    auto token = f2.repo().bind(my_path, f1);
    f2.start();
    vespalib::string expect("HTTP/1.1 200 OK\r\n"
                            "Connection: close\r\n"
                            "Content-Type: application/json\r\n"
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
    f4.start();
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
    vespalib::string get(const vespalib::string &host, const vespalib::string &,
                         const std::map<vespalib::string,vespalib::string> &) const override
    {
        return "[\"" + host + "\"]";
    }
};

TEST_FF("require that host is passed correctly", EchoHost(), HttpServer(0)) {
    auto token = f2.repo().bind(my_path, f1);
    f2.start();
    EXPECT_EQUAL(make_string("%s:%d", HostName::get().c_str(), f2.port()), f2.host());
    vespalib::string default_result = make_string("[\"%s\"]", f2.host().c_str());
    vespalib::string localhost_result = make_string("[\"%s:%d\"]", "localhost", f2.port());
    vespalib::string silly_result = "[\"sillyserver\"]";
    EXPECT_EQUAL(localhost_result, run_cmd(make_string("curl -s http://localhost:%d/my/path", f2.port())));
    EXPECT_EQUAL(silly_result, run_cmd(make_string("curl -s http://localhost:%d/my/path -H \"Host: sillyserver\"", f2.port())));
    EXPECT_EQUAL(default_result, run_cmd(make_string("curl -s http://localhost:%d/my/path -H \"Host:\"", f2.port())));
}

//-----------------------------------------------------------------------------

TEST_FFFF("require that the state server wires the appropriate url prefixes",
          SimpleHealthProducer(), SimpleMetricsProducer(), SimpleComponentConfigProducer(),
          StateServer(0, f1, f2, f3))
{
    f2.setTotalMetrics("{}"); // avoid empty result
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
    EXPECT_EQUAL("[1]", f4.get("", "/foo", params));
    EXPECT_EQUAL("[2]", f4.get("", "/foo/bar", params));
    EXPECT_EQUAL("[3]", f4.get("", "/foo/bar/baz", params));
    token2.reset();
    EXPECT_EQUAL("[1]", f4.get("", "/foo", params));
    EXPECT_EQUAL("[1]", f4.get("", "/foo/bar", params));
    EXPECT_EQUAL("[3]", f4.get("", "/foo/bar/baz", params));
}

TEST_FFFF("require that json handlers can be shadowed",
          DummyHandler("[1]"), DummyHandler("[2]"), DummyHandler("[3]"),
          JsonHandlerRepo())
{
    auto token1 = f4.bind("/foo", f1);
    auto token2 = f4.bind("/foo/bar", f2);
    std::map<vespalib::string,vespalib::string> params;
    EXPECT_EQUAL("[1]", f4.get("", "/foo", params));
    EXPECT_EQUAL("[2]", f4.get("", "/foo/bar", params));
    auto token3 = f4.bind("/foo/bar", f3);
    EXPECT_EQUAL("[3]", f4.get("", "/foo/bar", params));
    token3.reset();
    EXPECT_EQUAL("[2]", f4.get("", "/foo/bar", params));
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
    f2.setTotalMetrics("{}"); // avoid empty result
    EXPECT_TRUE(!f4.get(host_tag, short_root_path, empty_params).empty());
    EXPECT_TRUE(!f4.get(host_tag, root_path, empty_params).empty());
    EXPECT_TRUE(!f4.get(host_tag, health_path, empty_params).empty());
    EXPECT_TRUE(!f4.get(host_tag, metrics_path, empty_params).empty());
    EXPECT_TRUE(!f4.get(host_tag, config_path, empty_params).empty());
    EXPECT_TRUE(!f4.get(host_tag, total_metrics_path, empty_params).empty());
    EXPECT_TRUE(f4.get(host_tag, unknown_path, empty_params).empty());
    EXPECT_TRUE(f4.get(host_tag, unknown_state_path, empty_params).empty());
}

TEST_FFFF("require that top-level urls are generated correctly",
          SimpleHealthProducer(), SimpleMetricsProducer(), SimpleComponentConfigProducer(),
          StateApi(f1, f2, f3))
{
    EXPECT_EQUAL("{\"resources\":["
                 "{\"url\":\"http://HOST/state/v1/health\"},"
                 "{\"url\":\"http://HOST/state/v1/metrics\"},"
                 "{\"url\":\"http://HOST/state/v1/config\"}]}",
                 f4.get(host_tag, root_path, empty_params));
    EXPECT_EQUAL(f4.get(host_tag, root_path, empty_params),
                 f4.get(host_tag, short_root_path, empty_params));
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
                 f4.get(host_tag, root_path, empty_params));
}

TEST_FFFF("require that health resource works as expected",
          SimpleHealthProducer(), SimpleMetricsProducer(), SimpleComponentConfigProducer(),
          StateApi(f1, f2, f3))
{
    EXPECT_EQUAL("{\"status\":{\"code\":\"up\"}}",
                 f4.get(host_tag, health_path, empty_params));
    f1.setFailed("FAIL MSG");
    EXPECT_EQUAL("{\"status\":{\"code\":\"down\",\"message\":\"FAIL MSG\"}}",
                 f4.get(host_tag, health_path, empty_params));
}

TEST_FFFF("require that metrics resource works as expected",
          SimpleHealthProducer(), SimpleMetricsProducer(), SimpleComponentConfigProducer(),
          StateApi(f1, f2, f3))
{
    EXPECT_EQUAL("{\"status\":{\"code\":\"up\"}}",
                 f4.get(host_tag, metrics_path, empty_params));
    f1.setFailed("FAIL MSG");
    EXPECT_EQUAL("{\"status\":{\"code\":\"down\",\"message\":\"FAIL MSG\"}}",
                 f4.get(host_tag, metrics_path, empty_params));
    f1.setOk();
    f2.setMetrics("{\"foo\":\"bar\"}");
    EXPECT_EQUAL("{\"status\":{\"code\":\"up\"},\"metrics\":{\"foo\":\"bar\"}}",
                 f4.get(host_tag, metrics_path, empty_params));
}

TEST_FFFF("require that config resource works as expected",
          SimpleHealthProducer(), SimpleMetricsProducer(), SimpleComponentConfigProducer(),
          StateApi(f1, f2, f3))
{
    EXPECT_EQUAL("{\"config\":{}}",
                 f4.get(host_tag, config_path, empty_params));
    f3.addConfig(SimpleComponentConfigProducer::Config("foo", 3));
    EXPECT_EQUAL("{\"config\":{\"generation\":3,\"foo\":{\"generation\":3}}}",
                 f4.get(host_tag, config_path, empty_params));
    f3.addConfig(SimpleComponentConfigProducer::Config("foo", 4));
    f3.addConfig(SimpleComponentConfigProducer::Config("bar", 4, "error"));
    EXPECT_EQUAL("{\"config\":{\"generation\":4,\"bar\":{\"generation\":4,\"message\":\"error\"},\"foo\":{\"generation\":4}}}",
                 f4.get(host_tag, config_path, empty_params));
    f3.removeConfig("bar");
    EXPECT_EQUAL("{\"config\":{\"generation\":4,\"foo\":{\"generation\":4}}}",
                 f4.get(host_tag, config_path, empty_params));
}

TEST_FFFF("require that state api also can return total metric",
          SimpleHealthProducer(), SimpleMetricsProducer(), SimpleComponentConfigProducer(),
          StateApi(f1, f2, f3))
{
    f2.setTotalMetrics("{\"foo\":\"bar\"}");
    EXPECT_EQUAL("{\"foo\":\"bar\"}",
                 f4.get(host_tag, total_metrics_path, empty_params));
}

TEST_FFFFF("require that custom handlers can be added to the state server",
          SimpleHealthProducer(), SimpleMetricsProducer(), SimpleComponentConfigProducer(),
          StateApi(f1, f2, f3), DummyHandler("[123]"))
{
    EXPECT_EQUAL("", f4.get(host_tag, my_path, empty_params));
    auto token = f4.repo().bind(my_path, f5);
    EXPECT_EQUAL("[123]", f4.get(host_tag, my_path, empty_params));
    token.reset();
    EXPECT_EQUAL("", f4.get(host_tag, my_path, empty_params));
}

struct EchoConsumer : MetricsProducer {
    vespalib::string getMetrics(const vespalib::string &consumer) override {
        return "[\"" + consumer + "\"]";
    }
    vespalib::string getTotalMetrics(const vespalib::string &consumer) override {
        return "[\"" + consumer + "\"]";
    }
};

TEST_FFFF("require that empty v1 metrics consumer defaults to 'statereporter'",
          SimpleHealthProducer(), EchoConsumer(), SimpleComponentConfigProducer(),
          StateApi(f1, f2, f3))
{
    std::map<vespalib::string,vespalib::string> my_params;
    EXPECT_EQUAL("{\"status\":{\"code\":\"up\"},\"metrics\":[\"statereporter\"]}", f4.get(host_tag, metrics_path, empty_params));
}

TEST_FFFF("require that empty total metrics consumer defaults to the empty string",
          SimpleHealthProducer(), EchoConsumer(), SimpleComponentConfigProducer(),
          StateApi(f1, f2, f3))
{
    std::map<vespalib::string,vespalib::string> my_params;
    EXPECT_EQUAL("[\"\"]", f4.get(host_tag, total_metrics_path, empty_params));
}

TEST_FFFF("require that metrics consumer is passed correctly",
          SimpleHealthProducer(), EchoConsumer(), SimpleComponentConfigProducer(),
          StateApi(f1, f2, f3))
{
    std::map<vespalib::string,vespalib::string> my_params;
    my_params["consumer"] = "ME";
    EXPECT_EQUAL("{\"status\":{\"code\":\"up\"},\"metrics\":[\"ME\"]}", f4.get(host_tag, metrics_path, my_params));
    EXPECT_EQUAL("[\"ME\"]", f4.get(host_tag, total_metrics_path, my_params));
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
    EXPECT_EQUAL("", state_handler.get(host_tag, unknown_path, empty_params));
    EXPECT_EQUAL("", state_handler.get(host_tag, unknown_state_path, empty_params));
    check_json(json_root, state_handler.get(host_tag, root_path, empty_params));
    check_json(json_engine, state_handler.get(host_tag, root_path + "engine", empty_params));
    check_json(json_engine_stats, state_handler.get(host_tag, root_path + "engine/stats", empty_params));
    check_json(json_list, state_handler.get(host_tag, root_path + "list", empty_params));
    check_json(json_list_one, state_handler.get(host_tag, root_path + "list/one", empty_params));
    check_json(json_list_one_size, state_handler.get(host_tag, root_path + "list/one/size", empty_params));
    check_json(json_list_two, state_handler.get(host_tag, root_path + "list/two", empty_params));
}

TEST_MAIN_WITH_PROCESS_PROXY() { TEST_RUN_ALL(); }
