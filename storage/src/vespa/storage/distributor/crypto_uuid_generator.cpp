// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "crypto_uuid_generator.h"
#include <vespa/vespalib/crypto/random.h>

namespace storage::distributor {

vespalib::string CryptoUuidGenerator::generate_uuid() const {
    unsigned char rand_buf[16];
    vespalib::crypto::random_buffer(rand_buf, sizeof(rand_buf));
    const char hex[16+1] = "0123456789abcdef";
    vespalib::string ret(sizeof(rand_buf) * 2, '\0');
    for (size_t i = 0; i < sizeof(rand_buf); ++i) {
        ret[i*2 + 0] = hex[rand_buf[i] >> 4];
        ret[i*2 + 1] = hex[rand_buf[i] & 0x0f];
    }
    return ret;
}

}
