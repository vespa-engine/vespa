// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <memory>

namespace vespalib::crypto {

/*
 * Represents an asymmetric cryptographic private key.
 *
 * Can only be used for private/public key crypto, not for secret key (e.g. AES) crypto.
 * Currently only supports generating EC keys on the standard P-256 curve.
 */
class PrivateKey {
public:
    enum class Type {
        EC,
        RSA // TODO implement support..!
    };

    virtual ~PrivateKey() = default;

    virtual Type type() const noexcept = 0;
    // TODO should have a wrapper for this that takes care to securely erase
    // string memory on destruction.
    virtual vespalib::string private_to_pem() const = 0;

    static std::shared_ptr<PrivateKey> generate_p256_ec_key();
protected:
    PrivateKey() = default;
};

}
