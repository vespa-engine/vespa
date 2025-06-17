// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/portal/http_request.h>
#include <vespa/vespalib/util/stringfmt.h>

using namespace vespalib;
using namespace vespalib::portal;

std::string simple_req("GET /my/path HTTP/1.1\r\n"
                            "Host: my.host.com:80\r\n"
                            "CustomHeader: CustomValue\r\n"
                            "\r\n123456789");
size_t simple_req_padding = 9;
size_t simple_req_size = (simple_req.size() - simple_req_padding);

void verify_simple_req(const HttpRequest &req) {
    EXPECT_TRUE(!req.need_more_data());
    EXPECT_TRUE(req.valid());
    EXPECT_TRUE(req.is_get());
    EXPECT_EQ(req.get_uri(), "/my/path");
    EXPECT_EQ(req.get_header("host"), "my.host.com:80");
    EXPECT_EQ(req.get_header("customheader"), "CustomValue");
    EXPECT_EQ(req.get_header("non-existing-header"), "");
}

HttpRequest make_request(const std::string &req) {
    HttpRequest result;
    EXPECT_EQ(result.handle_data(req.data(), req.size()), req.size());
    EXPECT_TRUE(result.valid());
    return result;
}

void verify_invalid_request(const std::string &req) {
    HttpRequest result;
    EXPECT_EQ(result.handle_data(req.data(), req.size()), req.size());
    EXPECT_TRUE(!result.need_more_data());
    EXPECT_TRUE(!result.valid());
}

TEST(HttpRequestTest, require_that_request_can_be_parsed_in_one_go) {
    HttpRequest req;
    EXPECT_EQ(req.handle_data(simple_req.data(), simple_req_size), simple_req_size);
    GTEST_DO(verify_simple_req(req));
}

TEST(HttpRequestTest, require_that_trailing_data_is_not_consumed) {
    HttpRequest req;
    EXPECT_EQ(req.handle_data(simple_req.data(), simple_req.size()), simple_req_size);
    GTEST_DO(verify_simple_req(req));
}

TEST(HttpRequestTest, require_that_request_can_be_parsed_incrementally) {
    HttpRequest req;
    size_t chunk = 7;
    size_t done = 0;
    while (done < simple_req_size) {
        size_t expect = std::min(simple_req_size - done, chunk);
        EXPECT_EQ(req.handle_data(simple_req.data() + done, chunk), expect);
        done += expect;
    }
    EXPECT_EQ(done, simple_req_size);
    GTEST_DO(verify_simple_req(req));
}

TEST(HttpRequestTest, require_that_header_continuation_is_replaced_by_single_space) {
    auto req = make_request("GET /my/path HTTP/1.1\r\n"
                            "test: one\r\n"
                            " two\r\n"
                            "\tthree\r\n"
                            "\r\n");
    EXPECT_EQ(req.get_header("test"), "one two three");
}

TEST(HttpRequestTest, require_that_duplicate_headers_are_combined_as_list) {
    auto req = make_request("GET /my/path HTTP/1.1\r\n"
                            "test: one\r\n"
                            "test: two\r\n"
                            "test: three\r\n"
                            "\r\n");
    EXPECT_EQ(req.get_header("test"), "one,two,three");
}

TEST(HttpRequestTest, require_that_leading_and_trailing_whitespaces_are_stripped) {
    auto req = make_request("GET /my/path HTTP/1.1\r\n"
                            "test:   one  \r\n"
                            "        , two  \r\n"
                            "test:   three   \r\n"
                            "\r\n");
    EXPECT_EQ(req.get_header("test"), "one , two,three");
}

TEST(HttpRequestTest, require_that_non_GET_requests_are_detected) {
    auto req = make_request("POST /my/path HTTP/1.1\r\n"
                            "\r\n");
    EXPECT_TRUE(!req.is_get());
}

TEST(HttpRequestTest, require_that_request_line_must_contain_all_relevant_parts) {
    GTEST_DO(verify_invalid_request("/my/path HTTP/1.1\r\n"));
    GTEST_DO(verify_invalid_request("GET HTTP/1.1\r\n"));
    GTEST_DO(verify_invalid_request("GET /my/path\r\n"));
}

TEST(HttpRequestTest, require_that_first_header_line_cannot_be_a_continuation) {
    GTEST_DO(verify_invalid_request("GET /my/path HTTP/1.1\r\n"
                                   " two\r\n"));
}

TEST(HttpRequestTest, require_that_header_name_is_not_allowed_to_be_empty) {
    GTEST_DO(verify_invalid_request("GET /my/path HTTP/1.1\r\n"
                                   ": value\r\n"));
}

TEST(HttpRequestTest, require_that_header_line_must_contain_separator) {
    GTEST_DO(verify_invalid_request("GET /my/path HTTP/1.1\r\n"
                                   "ok-header: ok-value\r\n"
                                   "missing separator\r\n"));
}

TEST(HttpRequestTest, require_that_uri_parameters_can_be_parsed) {
    auto req = make_request("GET /my/path?foo=bar&baz HTTP/1.1\r\n\r\n");
    EXPECT_EQ(req.get_uri(), "/my/path?foo=bar&baz");
    EXPECT_EQ(req.get_path(), "/my/path");
    EXPECT_TRUE(req.has_param("foo"));
    EXPECT_TRUE(!req.has_param("bar"));
    EXPECT_TRUE(req.has_param("baz"));
    EXPECT_EQ(req.get_param("foo"), "bar");
    EXPECT_EQ(req.get_param("bar"), "");
    EXPECT_EQ(req.get_param("baz"), "");
}

TEST(HttpRequestTest, require_that_byte_values_in_uri_segments__path_key_value__are_dequoted_as_expected) {
    std::string str = "0123456789aBcDeF";
    for (size_t a = 0; a < 16; ++a) {
        for (size_t b = 0; b < 16; ++b) {
            std::string expect = " foo ";
            expect.push_back((a * 16) + b);
            expect.push_back((a * 16) + b);
            expect.append(" bar ");
            std::string input = vespalib::make_string("+foo+%%%c%c%%%c%c+bar+",
                    str[a], str[b], str[a], str[b]);
            std::string uri = vespalib::make_string("%s?%s=%s&extra=yes",
                    input.c_str(), input.c_str(), input.c_str());
            auto req = make_request(vespalib::make_string("GET %s HTTP/1.1\r\n\r\n",
                            uri.c_str()));
            EXPECT_EQ(req.get_uri(), uri);
            EXPECT_EQ(req.get_path(), expect);
            EXPECT_TRUE(req.has_param(expect));
            EXPECT_EQ(req.get_param(expect), expect);
            EXPECT_TRUE(req.has_param("extra"));
            EXPECT_EQ(req.get_param("extra"), "yes");
        }
    }
}

TEST(HttpRequestTest, require_that_percent_character_becomes_plain_if_not_followed_by_exactly_2_hex_digits) {
    auto req = make_request("GET %/5%5:%@5%5G%`5%5g%5?% HTTP/1.1\r\n\r\n");
    EXPECT_EQ(req.get_path(), "%/5%5:%@5%5G%`5%5g%5");
    EXPECT_TRUE(req.has_param("%"));
}

TEST(HttpRequestTest, require_that_last_character_of_uri_segments__path_key_value__can_be_quoted) {
    auto req = make_request("GET /%41?%42=%43 HTTP/1.1\r\n\r\n");
    EXPECT_EQ(req.get_path(), "/A");
    EXPECT_EQ(req.get_param("B"), "C");
}

TEST(HttpRequestTest, require_that_additional_query_and_key_value_separators_are_not_special) {
    auto req = make_request("GET /?" "?== HTTP/1.1\r\n\r\n");
    EXPECT_EQ(req.get_path(), "/");
    EXPECT_EQ(req.get_param("?"), "=");
}

GTEST_MAIN_RUN_ALL_TESTS()
