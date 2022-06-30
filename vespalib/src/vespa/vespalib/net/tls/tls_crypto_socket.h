// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/net/crypto_socket.h>

namespace vespalib {

struct TlsCryptoSocket : public CryptoSocket {
    ~TlsCryptoSocket() override;
    virtual void inject_read_data(const char *buf, size_t len) = 0;
};

} // namespace vespalib
