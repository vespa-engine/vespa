// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "private_key.h"
#include "openssl_crypto_impl.h"

namespace vespalib::crypto {

std::shared_ptr<PrivateKey> PrivateKey::generate_p256_ec_key() {
    return openssl_impl::PrivateKeyImpl::generate_openssl_p256_ec_key();
}

}
