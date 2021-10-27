// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/net/socket_spec.h>

using namespace vespalib;

void verify(const SocketSpec &spec, bool valid,
            const vespalib::string &path, const vespalib::string &name,
            const vespalib::string &host, const vespalib::string &host_with_fallback,
            int port)
{
    EXPECT_EQUAL(spec.valid(), valid);
    EXPECT_EQUAL(spec.path(), path);
    EXPECT_EQUAL(spec.name(), name);
    EXPECT_EQUAL(spec.host(), host);
    EXPECT_EQUAL(spec.host_with_fallback(), host_with_fallback);
    EXPECT_EQUAL(spec.port(), port);
}

void verify_path(const SocketSpec &spec, const vespalib::string &path) {
    TEST_DO(verify(spec, true, path, "", "", "", -1));
}

void verify_name(const SocketSpec &spec, const vespalib::string &name) {
    TEST_DO(verify(spec, true, "", name, "", "", -1));
}

void verify_host_port(const SocketSpec &spec, const vespalib::string &host, int port) {
    TEST_DO(verify(spec, true, "", "", host, host, port));
}

void verify_port(const SocketSpec &spec, int port) {
    TEST_DO(verify(spec, true, "", "", "", "localhost", port));
}

void verify_invalid(const SocketSpec &spec) {
    TEST_DO(verify(spec, false, "", "", "", "", -1));
}

void verify_spec(const vespalib::string &str, const vespalib::string &expect) {
    vespalib::string actual = SocketSpec(str).spec();
    EXPECT_EQUAL(actual, expect);
}

void verify_spec(const vespalib::string &str) {
    TEST_DO(verify_spec(str, str));
}

//-----------------------------------------------------------------------------

TEST("require that socket spec can be created directly from path") {
    TEST_DO(verify_path(SocketSpec::from_path("my_path"), "my_path"));
}

TEST("require that socket spec can be created directly from name") {
    TEST_DO(verify_name(SocketSpec::from_name("my_name"), "my_name"));
}

TEST("require that socket spec can be created directly from host and port") {
    TEST_DO(verify_host_port(SocketSpec::from_host_port("my_host", 123), "my_host", 123));
}

TEST("require that socket spec can be created directly from port only") {
    TEST_DO(verify_port(SocketSpec::from_port(123), 123));
}

TEST("require that socket spec parsing works as expected") {
    TEST_DO(verify_invalid(SocketSpec("")));
    TEST_DO(verify_invalid(SocketSpec("bogus")));
    TEST_DO(verify_path(SocketSpec("ipc/file:my_path"), "my_path"));
    TEST_DO(verify_invalid(SocketSpec("ipc/file:")));
    TEST_DO(verify_name(SocketSpec("ipc/name:my_name"), "my_name"));
    TEST_DO(verify_invalid(SocketSpec("ipc/name:")));
    TEST_DO(verify_host_port(SocketSpec("tcp/my_host:123"), "my_host", 123));
    TEST_DO(verify_port(SocketSpec("tcp/123"), 123));
    TEST_DO(verify_port(SocketSpec("tcp/0"), 0));
    TEST_DO(verify_invalid(SocketSpec("tcp/:123")));
    TEST_DO(verify_invalid(SocketSpec("tcp/:0")));
    TEST_DO(verify_invalid(SocketSpec("tcp/host:xyz")));
    TEST_DO(verify_invalid(SocketSpec("tcp/xyz")));
    TEST_DO(verify_invalid(SocketSpec("tcp/host:-123")));
    TEST_DO(verify_invalid(SocketSpec("tcp/-123")));
    TEST_DO(verify_invalid(SocketSpec("tcp/host:")));
    TEST_DO(verify_invalid(SocketSpec("tcp/")));
    TEST_DO(verify_host_port(SocketSpec("tcp/[my:host]:123"), "my:host", 123));
    TEST_DO(verify_invalid(SocketSpec("tcp/[]:123")));
    TEST_DO(verify_host_port(SocketSpec("tcp/[:123"), "[", 123));
    TEST_DO(verify_host_port(SocketSpec("tcp/]:123"), "]", 123));
    TEST_DO(verify_host_port(SocketSpec("tcp/my:host:123"), "my:host", 123));
}

TEST("require that socket spec to string transform works as expected") {
    TEST_DO(verify_spec("invalid"));
    TEST_DO(verify_spec("bogus", "invalid"));
    TEST_DO(verify_spec("ipc/file:my_path"));
    TEST_DO(verify_spec("ipc/name:my_name"));
    TEST_DO(verify_spec("tcp/123"));
    TEST_DO(verify_spec("tcp/0"));
    TEST_DO(verify_spec("tcp/host:123"));
    TEST_DO(verify_spec("tcp/[my:host]:123"));
    TEST_DO(verify_spec("tcp/[host]:123", "tcp/host:123"));
}

TEST("require that port-only spec resolves to wildcard server address") {
    EXPECT_TRUE(SocketSpec("tcp/123").server_address().is_wildcard());
}

TEST("require that port-only spec resolves to non-wildcard client address") {
    EXPECT_TRUE(!SocketSpec("tcp/123").client_address().is_wildcard());
}

TEST("require that replace_host makes new spec with replaced host") {
    SocketSpec old_spec("tcp/host:123");
    const SocketSpec &const_spec = old_spec;
    SocketSpec new_spec = const_spec.replace_host("foo");
    TEST_DO(verify_host_port(old_spec, "host", 123));
    TEST_DO(verify_host_port(new_spec, "foo", 123));
}

TEST("require that replace_host gives invalid spec when used with less than 2 host names") {
    TEST_DO(verify_invalid(SocketSpec("bogus").replace_host("foo")));
    TEST_DO(verify_invalid(SocketSpec("tcp/123").replace_host("foo")));
    TEST_DO(verify_invalid(SocketSpec("tcp/host:123").replace_host("")));
    TEST_DO(verify_invalid(SocketSpec("ipc/file:my_socket").replace_host("foo")));
    TEST_DO(verify_invalid(SocketSpec("ipc/name:my_socket").replace_host("foo")));
}

TEST("require that invalid socket spec is not valid") {
    EXPECT_FALSE(SocketSpec::invalid.valid());
}

TEST_MAIN() { TEST_RUN_ALL(); }
