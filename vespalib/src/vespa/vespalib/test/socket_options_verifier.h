// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/gtest/gtest.h>
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
    EXPECT_EQ(getsockopt(fd, level, name, &data, &size), 0);
    EXPECT_EQ(size, sizeof(data));
    EXPECT_EQ(data != 0, expect);
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
        EXPECT_NE(flags, -1);
        EXPECT_EQ(((flags & O_NONBLOCK) == 0), value);
    }
    void verify_nodelay(bool value) {
        SCOPED_TRACE("verify nodelay");
        verify_bool_opt(fd, IPPROTO_TCP, TCP_NODELAY, value);
    }
    void verify_reuse_addr(bool value) {
        SCOPED_TRACE("verify reuse addr");
        verify_bool_opt(fd, SOL_SOCKET, SO_REUSEADDR, value);
    }
    void verify_ipv6_only(bool value) {
        SCOPED_TRACE("verify ipv6 only");
        verify_bool_opt(fd, IPPROTO_IPV6, IPV6_V6ONLY, value);
    }
    void verify_keepalive(bool value) {
        SCOPED_TRACE("verify keepalive");
        verify_bool_opt(fd, SOL_SOCKET, SO_KEEPALIVE, value);
    }
    void verify_linger(bool enable, int value)
    {
        struct linger data;
        socklen_t size = sizeof(data);
        memset(&data, 0, sizeof(data));
        EXPECT_EQ(getsockopt(fd, SOL_SOCKET, SO_LINGER, &data, &size), 0);
        EXPECT_EQ(size, sizeof(data));
        EXPECT_EQ(enable, data.l_onoff);
        if (enable) {
            EXPECT_EQ(value, data.l_linger);
        }
    }
};

}
