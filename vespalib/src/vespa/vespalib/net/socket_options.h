// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vespalib {

/**
 * Low-level functions used to adjust various socket related
 * options. Return values indicate success/failure.
 **/
struct SocketOptions {
    static bool set_blocking(int fd, bool value);
    static bool set_nodelay(int fd, bool value);
    static bool set_reuse_addr(int fd, bool value);
    static bool set_ipv6_only(int fd, bool value);
    static bool set_keepalive(int fd, bool value);
    static bool set_linger(int fd, bool enable, int value);
};

} // namespace vespalib
