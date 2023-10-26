// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/testkit/test_kit.h>
#include <fcntl.h>
#include <unistd.h>
#include <netinet/tcp.h>
#include <sys/socket.h>
#include <netinet/in.h>

namespace vespalib::test {

namespace {

void verify_bool_opt(int fd, int level, int name, bool expect) {
    int data = 0;
    socklen_t size = sizeof(data);
    EXPECT_EQUAL(getsockopt(fd, level, name, &data, &size), 0);
    EXPECT_EQUAL(size, sizeof(data));
    EXPECT_EQUAL(data != 0, expect);
}

} // namespace vespalib::test::<unnamed>

/**
 * Verifier of socket options for testing purposes
 **/
struct SocketOptionsVerifier {
    int fd;
    SocketOptionsVerifier(int fd_in) : fd(fd_in) {}
    void verify_blocking(bool value) {
        int flags = fcntl(fd, F_GETFL, NULL);
        EXPECT_NOT_EQUAL(flags, -1);
        EXPECT_EQUAL(((flags & O_NONBLOCK) == 0), value);
    }
    void verify_nodelay(bool value) {
        TEST_DO(verify_bool_opt(fd, IPPROTO_TCP, TCP_NODELAY, value));
    }
    void verify_reuse_addr(bool value) {
        TEST_DO(verify_bool_opt(fd, SOL_SOCKET, SO_REUSEADDR, value));
    }
    void verify_ipv6_only(bool value) {
        TEST_DO(verify_bool_opt(fd, IPPROTO_IPV6, IPV6_V6ONLY, value));
    }
    void verify_keepalive(bool value) {
        TEST_DO(verify_bool_opt(fd, SOL_SOCKET, SO_KEEPALIVE, value));
    }
    void verify_linger(bool enable, int value)
    {
        struct linger data;
        socklen_t size = sizeof(data);
        memset(&data, 0, sizeof(data));
        EXPECT_EQUAL(getsockopt(fd, SOL_SOCKET, SO_LINGER, &data, &size), 0);
        EXPECT_EQUAL(size, sizeof(data));
        EXPECT_EQUAL(enable, data.l_onoff);
        if (enable) {
            EXPECT_EQUAL(value, data.l_linger);
        }
    }
};

}
