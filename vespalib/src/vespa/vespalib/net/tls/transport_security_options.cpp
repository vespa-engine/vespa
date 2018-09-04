// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "transport_security_options.h"
#include <openssl/crypto.h>

namespace vespalib::net::tls {

TransportSecurityOptions::~TransportSecurityOptions() {
    OPENSSL_cleanse(&_private_key_pem[0], _private_key_pem.size());
}

}
