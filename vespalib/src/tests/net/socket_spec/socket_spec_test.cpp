// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/net/socket_spec.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <iomanip>
#include <ostream>

using namespace vespalib;

namespace vespalib {

void PrintTo(const SocketSpec& spec, std::ostream* os) {
    *os << std::boolalpha << "{valid=" << spec.valid() << ", path=" << std::quoted(spec.path()) <<
        ", name= " << std::quoted(spec.name()) << ", host=" << std::quoted(spec.host()) <<
        ", host_with_fallback=" << std::quoted(spec.host_with_fallback()) << ", port=" << spec.port() << "}";
}

}

bool verify(const SocketSpec &spec, bool valid, const std::string &path, const std::string &name,
            const std::string &host, const std::string &host_with_fallback, int port)
{
    bool retval = true;
    EXPECT_EQ(spec.valid(), valid) << (retval = false, "");
    EXPECT_EQ(spec.path(), path) << (retval = false, "");
    EXPECT_EQ(spec.name(), name) << (retval = false, "");
    EXPECT_EQ(spec.host(), host) << (retval = false, "");
    EXPECT_EQ(spec.host_with_fallback(), host_with_fallback) << (retval = false, "");
    EXPECT_EQ(spec.port(), port) << (retval = false, "");
    return retval;;
}

bool has_only_path(const SocketSpec& spec, const std::string& path) {
    return verify(spec, true, path, "", "", "", -1);
}

bool has_only_name(const SocketSpec& spec, const std::string& name) {
    return verify(spec, true, "", name, "", "", -1);
}

bool has_only_host_port(const SocketSpec &spec, const std::string &host, int port) {
    return verify(spec, true, "", "", host, host, port);
}

bool has_only_port(const SocketSpec &spec, int port) {
    return verify(spec, true, "", "", "", "localhost", port);
}

bool is_invalid(const SocketSpec &spec) {
    return verify(spec, false, "", "", "", "", -1);
}

struct HasSpec {
    bool operator()(const std::string &str, const std::string &expected) const {
        bool retval = true;
        EXPECT_EQ(SocketSpec(str).spec(), expected) << (retval = false, "");
        return retval;
    }
    bool operator()(const std::string& str) const {
        return operator()(str, str);
    }
} has_spec;

//-----------------------------------------------------------------------------

TEST(SocketSpecTest, require_that_socket_spec_can_be_created_directly_from_path) {
    EXPECT_PRED2(has_only_path, SocketSpec::from_path("my_path"), "my_path");
}

TEST(SocketSpecTest, require_that_socket_spec_can_be_created_directly_from_name) {
    EXPECT_PRED2(has_only_name, SocketSpec::from_name("my_name"), "my_name");
}

TEST(SocketSpecTest, require_that_socket_spec_can_be_created_directly_from_host_and_port) {
    EXPECT_PRED3(has_only_host_port, SocketSpec::from_host_port("my_host", 123), "my_host", 123);
}

TEST(SocketSpecTest, require_that_socket_spec_can_be_created_directly_from_port_only) {
    EXPECT_PRED2(has_only_port, SocketSpec::from_port(123), 123);
}

TEST(SocketSpecTest, require_that_socket_spec_parsing_works_as_expected) {
    EXPECT_PRED1(is_invalid, SocketSpec(""));
    EXPECT_PRED1(is_invalid, SocketSpec("bogus"));
    EXPECT_PRED2(has_only_path, SocketSpec("ipc/file:my_path"), "my_path");
    EXPECT_PRED1(is_invalid, SocketSpec("ipc/file:"));
    EXPECT_PRED2(has_only_name, SocketSpec("ipc/name:my_name"), "my_name");
    EXPECT_PRED1(is_invalid, SocketSpec("ipc/name:"));
    EXPECT_PRED3(has_only_host_port, SocketSpec("tcp/my_host:123"), "my_host", 123);
    EXPECT_PRED2(has_only_port, SocketSpec("tcp/123"), 123);
    EXPECT_PRED2(has_only_port, SocketSpec("tcp/0"), 0);
    EXPECT_PRED1(is_invalid, SocketSpec("tcp/:123"));
    EXPECT_PRED1(is_invalid, SocketSpec("tcp/:0"));
    EXPECT_PRED1(is_invalid, SocketSpec("tcp/host:xyz"));
    EXPECT_PRED1(is_invalid, SocketSpec("tcp/xyz"));
    EXPECT_PRED1(is_invalid, SocketSpec("tcp/host:-123"));
    EXPECT_PRED1(is_invalid, SocketSpec("tcp/-123"));
    EXPECT_PRED1(is_invalid, SocketSpec("tcp/host:"));
    EXPECT_PRED1(is_invalid, SocketSpec("tcp/"));
    EXPECT_PRED3(has_only_host_port, SocketSpec("tcp/[my:host]:123"), "my:host", 123);
    EXPECT_PRED1(is_invalid, SocketSpec("tcp/[]:123"));
    EXPECT_PRED3(has_only_host_port, SocketSpec("tcp/[:123"), "[", 123);
    EXPECT_PRED3(has_only_host_port, SocketSpec("tcp/]:123"), "]", 123);
    EXPECT_PRED3(has_only_host_port, SocketSpec("tcp/my:host:123"), "my:host", 123);
}

TEST(SocketSpecTest, require_that_socket_spec_to_string_transform_works_as_expected) {
    EXPECT_PRED1(has_spec, "invalid");
    EXPECT_PRED2(has_spec, "bogus", "invalid");
    EXPECT_PRED1(has_spec, "ipc/file:my_path");
    EXPECT_PRED1(has_spec, "ipc/name:my_name");
    EXPECT_PRED1(has_spec, "tcp/123");
    EXPECT_PRED1(has_spec, "tcp/0");
    EXPECT_PRED1(has_spec, "tcp/host:123");
    EXPECT_PRED1(has_spec, "tcp/[my:host]:123");
    EXPECT_PRED2(has_spec, "tcp/[host]:123", "tcp/host:123");
}

TEST(SocketSpecTest, require_that_port_only_spec_resolves_to_wildcard_server_address) {
    EXPECT_TRUE(SocketSpec("tcp/123").server_address().is_wildcard());
}

TEST(SocketSpecTest, require_that_port_only_spec_resolves_to_non_wildcard_client_address) {
    EXPECT_TRUE(!SocketSpec("tcp/123").client_address().is_wildcard());
}

TEST(SocketSpecTest, require_that_replace_host_makes_new_spec_with_replaced_host) {
    SocketSpec old_spec("tcp/host:123");
    const SocketSpec &const_spec = old_spec;
    SocketSpec new_spec = const_spec.replace_host("foo");
    EXPECT_PRED3(has_only_host_port, old_spec, "host", 123);
    EXPECT_PRED3(has_only_host_port, new_spec, "foo", 123);
}

TEST(SocketSpecTest, require_that_replace_host_gives_invalid_spec_when_used_with_less_than_2_host_names) {
    EXPECT_PRED1(is_invalid, SocketSpec("bogus").replace_host("foo"));
    EXPECT_PRED1(is_invalid, SocketSpec("tcp/123").replace_host("foo"));
    EXPECT_PRED1(is_invalid, SocketSpec("tcp/host:123").replace_host(""));
    EXPECT_PRED1(is_invalid, SocketSpec("ipc/file:my_socket").replace_host("foo"));
    EXPECT_PRED1(is_invalid, SocketSpec("ipc/name:my_socket").replace_host("foo"));
}

TEST(SocketSpecTest, require_that_invalid_socket_spec_is_not_valid) {
    EXPECT_FALSE(SocketSpec::invalid.valid());
}

GTEST_MAIN_RUN_ALL_TESTS()
