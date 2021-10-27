// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <util/authority.h>
#include <vespa/vespalib/gtest/gtest.h>

using vespalib::SocketSpec;

//-----------------------------------------------------------------------------

TEST(MakeSNISpecTest, host_port_is_parsed_as_expected) {
    EXPECT_EQ(make_sni_spec("my_host:123", "fallback", 456, false).host(), "my_host");
    EXPECT_EQ(make_sni_spec("my_host:123", "fallback", 456, true).host(), "my_host");
    EXPECT_EQ(make_sni_spec("my_host:123", "fallback", 456, false).port(), 123);
    EXPECT_EQ(make_sni_spec("my_host:123", "fallback", 456, true).port(), 123);
}

TEST(MakeSNISpecTest, user_info_is_stripped) {
    EXPECT_EQ(make_sni_spec("myuser:deprecated@my_host:123", "fallback", 456, false).host(), "my_host");
    EXPECT_EQ(make_sni_spec("myuser:deprecated@my_host:123", "fallback", 456, true).host(), "my_host");
    EXPECT_EQ(make_sni_spec("myuser:deprecated@my_host:123", "fallback", 456, false).port(), 123);
    EXPECT_EQ(make_sni_spec("myuser:deprecated@my_host:123", "fallback", 456, true).port(), 123);
}

TEST(MakeSNISpecTest, port_can_be_skipped) {
    EXPECT_EQ(make_sni_spec("my_host", "fallback", 456, false).host(), "my_host");
    EXPECT_EQ(make_sni_spec("my_host", "fallback", 456, true).host(), "my_host");
    EXPECT_EQ(make_sni_spec("my_host", "fallback", 456, false).port(), 80);
    EXPECT_EQ(make_sni_spec("my_host", "fallback", 456, true).port(), 443);
}

TEST(MakeSNISpecTest, quoted_ip_addresses_work_as_expected) {
    EXPECT_EQ(make_sni_spec("[::1]:123", "fallback", 456, false).host(), "::1");
    EXPECT_EQ(make_sni_spec("[::1]:123", "fallback", 456, true).host(), "::1");
    EXPECT_EQ(make_sni_spec("[::1]:123", "fallback", 456, false).port(), 123);
    EXPECT_EQ(make_sni_spec("[::1]:123", "fallback", 456, true).port(), 123);
    EXPECT_EQ(make_sni_spec("[::1]", "fallback", 456, false).host(), "::1");
    EXPECT_EQ(make_sni_spec("[::1]", "fallback", 456, true).host(), "::1");
    EXPECT_EQ(make_sni_spec("[::1]", "fallback", 456, false).port(), 80);
    EXPECT_EQ(make_sni_spec("[::1]", "fallback", 456, true).port(), 443);
}

TEST(MakeSNISpecTest, supplied_host_port_is_used_as_fallback) {
    EXPECT_EQ(make_sni_spec("", "fallback", 456, false).host(), "fallback");
    EXPECT_EQ(make_sni_spec("", "fallback", 456, true).host(), "fallback");
    EXPECT_EQ(make_sni_spec("", "fallback", 456, false).port(), 456);
    EXPECT_EQ(make_sni_spec("", "fallback", 456, true).port(), 456);
}

//-----------------------------------------------------------------------------

TEST(MakeHostHeaderValueTest, host_port_is_formatted_as_expected) {
    auto my_spec = SocketSpec::from_host_port("myhost", 123);
    EXPECT_EQ(make_host_header_value(my_spec, false), "myhost:123");
    EXPECT_EQ(make_host_header_value(my_spec, true), "myhost:123");
}

TEST(MakeHostHeaderValueTest, inappropriate_spec_gives_empty_host_value) {
    std::vector<SocketSpec> bad_specs = {
        SocketSpec::invalid,
        SocketSpec::from_port(123),
        SocketSpec::from_name("foo"),
        SocketSpec::from_path("bar")
    };
    for (const auto &spec: bad_specs) {
        EXPECT_EQ(make_host_header_value(spec, false), "");
        EXPECT_EQ(make_host_header_value(spec, true), "");
    }
}

TEST(MakeHostHeaderValueTest, default_port_is_omitted) {
    auto spec1 = SocketSpec::from_host_port("myhost", 80);
    auto spec2 = SocketSpec::from_host_port("myhost", 443);
    EXPECT_EQ(make_host_header_value(spec1, false), "myhost");
    EXPECT_EQ(make_host_header_value(spec1, true), "myhost:80");
    EXPECT_EQ(make_host_header_value(spec2, false), "myhost:443");
    EXPECT_EQ(make_host_header_value(spec2, true), "myhost");
}

TEST(MakeHostHeaderValueTest, ipv6_addresses_are_quoted) {
    auto my_spec = SocketSpec::from_host_port("::1", 123);
    EXPECT_EQ(make_host_header_value(my_spec, false), "[::1]:123");
    EXPECT_EQ(make_host_header_value(my_spec, true), "[::1]:123");
}

//-----------------------------------------------------------------------------

GTEST_MAIN_RUN_ALL_TESTS()
