// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/net/socket_spec.h>

using namespace vespalib;

void verify(const SocketSpec &spec, bool valid, const vespalib::string &path, const vespalib::string &host, int port) {
    EXPECT_EQUAL(spec.valid(), valid);
    EXPECT_EQUAL(spec.path(), path);
    EXPECT_EQUAL(spec.host(), host);
    EXPECT_EQUAL(spec.port(), port);
}

void verify(const SocketSpec &spec, const vespalib::string &path) {
    TEST_DO(verify(spec, true, path, "", -1));
}

void verify(const SocketSpec &spec, const vespalib::string &host, int port) {
    TEST_DO(verify(spec, true, "", host, port));
}

void verify(const SocketSpec &spec, int port) {
    TEST_DO(verify(spec, true, "", "", port));
}

void verify_invalid(const SocketSpec &spec) {
    TEST_DO(verify(spec, false, "", "", -1));
}

//-----------------------------------------------------------------------------

TEST("require that socket spec can be created directly from path") {
    TEST_DO(verify(SocketSpec::from_path("my_path"), "my_path"));
}

TEST("require that socket spec can be created directly from host and port") {
    TEST_DO(verify(SocketSpec::from_host_port("my_host", 123), "my_host", 123));
}

TEST("require that socket spec can be created directly from port only") {
    TEST_DO(verify(SocketSpec::from_port(123), 123));
}

TEST("require that empty spec is invalid") {
    TEST_DO(verify_invalid(SocketSpec("")));
}

TEST("require that bogus spec is invalid") {
    TEST_DO(verify_invalid(SocketSpec("bogus")));
}

TEST("require that socket spec can parse ipc spec") {
    TEST_DO(verify(SocketSpec("ipc/file:my_path"), "my_path"));
}

TEST("require that empty ipc path gives invalid socket spec") {
    TEST_DO(verify_invalid(SocketSpec("ipc/file:")));
}

TEST("require that socket spec can parse host/port spec") {
    TEST_DO(verify(SocketSpec("tcp/my_host:123"), "my_host", 123));
}

TEST("require that socket spec can parse port only spec") {
    TEST_DO(verify(SocketSpec("tcp/123"), 123));
}

TEST("require that socket spec can parse the one true listen spec") {
    TEST_DO(verify(SocketSpec("tcp/0"), 0));
}

TEST("require that host port separator can be given also without host") {
    TEST_DO(verify(SocketSpec("tcp/:123"), 123));
    TEST_DO(verify(SocketSpec("tcp/:0"), 0));
}

TEST("require that non-number port gives invalid spec") {
    TEST_DO(verify_invalid(SocketSpec("tcp/host:xyz")));
    TEST_DO(verify_invalid(SocketSpec("tcp/xyz")));
}

TEST("require that negative port gives invalid spec") {
    TEST_DO(verify_invalid(SocketSpec("tcp/host:-123")));
    TEST_DO(verify_invalid(SocketSpec("tcp/-123")));
}

TEST("require that missing port number gives invalid spec") {
    TEST_DO(verify_invalid(SocketSpec("tcp/host:")));
    TEST_DO(verify_invalid(SocketSpec("tcp/")));
}

TEST("require that host can be quoted") {
    TEST_DO(verify(SocketSpec("tcp/[my:host]:123"), "my:host", 123));
}

TEST("require that missing host can be quoted") {
    TEST_DO(verify(SocketSpec("tcp/[]:123"), 123));
}

TEST("require that partial quotes are treated as host") {
    TEST_DO(verify(SocketSpec("tcp/[:123"), "[", 123));
    TEST_DO(verify(SocketSpec("tcp/]:123"), "]", 123));
}

TEST("require that inconvenient hosts can be parsed without quotes") {
    TEST_DO(verify(SocketSpec("tcp/my:host:123"), "my:host", 123));
}

TEST_MAIN() { TEST_RUN_ALL(); }
