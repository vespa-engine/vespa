// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/portal/http_request.h>
#include <vespa/vespalib/util/stringfmt.h>

using namespace vespalib;
using namespace vespalib::portal;

vespalib::string simple_req("GET /my/path HTTP/1.1\r\n"
                            "Host: my.host.com:80\r\n"
                            "CustomHeader: CustomValue\r\n"
                            "\r\n123456789");
size_t simple_req_padding = 9;
size_t simple_req_size = (simple_req.size() - simple_req_padding);

void verify_simple_req(const HttpRequest &req) {
    EXPECT_TRUE(!req.need_more_data());
    EXPECT_TRUE(req.valid());
    EXPECT_TRUE(req.is_get());
    EXPECT_EQUAL(req.get_uri(), "/my/path");
    EXPECT_EQUAL(req.get_header("host"), "my.host.com:80");
    EXPECT_EQUAL(req.get_header("customheader"), "CustomValue");
    EXPECT_EQUAL(req.get_header("non-existing-header"), "");
}

HttpRequest make_request(const vespalib::string &req) {
    HttpRequest result;
    ASSERT_EQUAL(result.handle_data(req.data(), req.size()), req.size());
    ASSERT_TRUE(result.valid());
    return result;
}

void verify_invalid_request(const vespalib::string &req) {
    HttpRequest result;
    EXPECT_EQUAL(result.handle_data(req.data(), req.size()), req.size());
    EXPECT_TRUE(!result.need_more_data());
    EXPECT_TRUE(!result.valid());
}

TEST("require that request can be parsed in one go") {
    HttpRequest req;
    EXPECT_EQUAL(req.handle_data(simple_req.data(), simple_req_size), simple_req_size);
    TEST_DO(verify_simple_req(req));
}

TEST("require that trailing data is not consumed") {
    HttpRequest req;
    EXPECT_EQUAL(req.handle_data(simple_req.data(), simple_req.size()), simple_req_size);
    TEST_DO(verify_simple_req(req));
}

TEST("require that request can be parsed incrementally") {
    HttpRequest req;
    size_t chunk = 7;
    size_t done = 0;
    while (done < simple_req_size) {
        size_t expect = std::min(simple_req_size - done, chunk);
        EXPECT_EQUAL(req.handle_data(simple_req.data() + done, chunk), expect);
        done += expect;
    }
    EXPECT_EQUAL(done, simple_req_size);
    TEST_DO(verify_simple_req(req));
}

TEST("require that header continuation is replaced by single space") {
    auto req = make_request("GET /my/path HTTP/1.1\r\n"
                            "test: one\r\n"
                            " two\r\n"
                            "\tthree\r\n"
                            "\r\n");
    EXPECT_EQUAL(req.get_header("test"), "one two three");
}

TEST("require that duplicate headers are combined as list") {
    auto req = make_request("GET /my/path HTTP/1.1\r\n"
                            "test: one\r\n"
                            "test: two\r\n"
                            "test: three\r\n"
                            "\r\n");
    EXPECT_EQUAL(req.get_header("test"), "one,two,three");
}

TEST("require that leading and trailing whitespaces are stripped") {
    auto req = make_request("GET /my/path HTTP/1.1\r\n"
                            "test:   one  \r\n"
                            "        , two  \r\n"
                            "test:   three   \r\n"
                            "\r\n");
    EXPECT_EQUAL(req.get_header("test"), "one , two,three");
}

TEST("require that non-GET requests are detected") {
    auto req = make_request("POST /my/path HTTP/1.1\r\n"
                            "\r\n");
    EXPECT_TRUE(!req.is_get());
}

TEST("require that request line must contain all relevant parts") {
    TEST_DO(verify_invalid_request("/my/path HTTP/1.1\r\n"));
    TEST_DO(verify_invalid_request("GET HTTP/1.1\r\n"));
    TEST_DO(verify_invalid_request("GET /my/path\r\n"));
}

TEST("require that first header line cannot be a continuation") {
    TEST_DO(verify_invalid_request("GET /my/path HTTP/1.1\r\n"
                                   " two\r\n"));
}

TEST("require that header name is not allowed to be empty") {
    TEST_DO(verify_invalid_request("GET /my/path HTTP/1.1\r\n"
                                   ": value\r\n"));
}

TEST("require that header line must contain separator") {
    TEST_DO(verify_invalid_request("GET /my/path HTTP/1.1\r\n"
                                   "ok-header: ok-value\r\n"
                                   "missing separator\r\n"));
}

TEST("require that uri parameters can be parsed") {
    auto req = make_request("GET /my/path?foo=bar&baz HTTP/1.1\r\n\r\n");
    EXPECT_EQUAL(req.get_uri(), "/my/path?foo=bar&baz");
    EXPECT_EQUAL(req.get_path(), "/my/path");
    EXPECT_TRUE(req.has_param("foo"));
    EXPECT_TRUE(!req.has_param("bar"));
    EXPECT_TRUE(req.has_param("baz"));
    EXPECT_EQUAL(req.get_param("foo"), "bar");
    EXPECT_EQUAL(req.get_param("bar"), "");
    EXPECT_EQUAL(req.get_param("baz"), "");
}

TEST("require that byte values in uri segments (path, key, value) are dequoted as expected") {
    vespalib::string str = "0123456789aBcDeF";
    for (size_t a = 0; a < 16; ++a) {
        for (size_t b = 0; b < 16; ++b) {
            vespalib::string expect = " foo ";
            expect.push_back((a * 16) + b);
            expect.push_back((a * 16) + b);
            expect.append(" bar ");
            vespalib::string input = vespalib::make_string("+foo+%%%c%c%%%c%c+bar+",
                    str[a], str[b], str[a], str[b]);
            vespalib::string uri = vespalib::make_string("%s?%s=%s&extra=yes",
                    input.c_str(), input.c_str(), input.c_str());
            auto req = make_request(vespalib::make_string("GET %s HTTP/1.1\r\n\r\n",
                            uri.c_str()));
            EXPECT_EQUAL(req.get_uri(), uri);
            EXPECT_EQUAL(req.get_path(), expect);
            EXPECT_TRUE(req.has_param(expect));
            EXPECT_EQUAL(req.get_param(expect), expect);
            EXPECT_TRUE(req.has_param("extra"));
            EXPECT_EQUAL(req.get_param("extra"), "yes");
        }
    }
}

TEST("require that percent character becomes plain if not followed by exactly 2 hex digits") {
    auto req = make_request("GET %/5%5:%@5%5G%`5%5g%5?% HTTP/1.1\r\n\r\n");
    EXPECT_EQUAL(req.get_path(), "%/5%5:%@5%5G%`5%5g%5");
    EXPECT_TRUE(req.has_param("%"));
}

TEST("require that last character of uri segments (path, key, value) can be quoted") {
    auto req = make_request("GET /%41?%42=%43 HTTP/1.1\r\n\r\n");
    EXPECT_EQUAL(req.get_path(), "/A");
    EXPECT_EQUAL(req.get_param("B"), "C");
}

TEST("require that additional query and key/value separators are not special") {
    auto req = make_request("GET /?" "?== HTTP/1.1\r\n\r\n");
    EXPECT_EQUAL(req.get_path(), "/");
    EXPECT_EQUAL(req.get_param("?"), "=");
}

TEST_MAIN() { TEST_RUN_ALL(); }
