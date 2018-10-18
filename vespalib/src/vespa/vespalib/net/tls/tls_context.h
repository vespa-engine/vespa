// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>

namespace vespalib::net::tls {

class TransportSecurityOptions;

struct TlsContext {
    virtual ~TlsContext() = default;

    static std::shared_ptr<TlsContext> create_default_context(const TransportSecurityOptions&);
};

}
