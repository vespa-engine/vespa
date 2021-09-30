// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "random.h"
#include <openssl/rand.h>

namespace vespalib::crypto {

void random_buffer(unsigned char* buf, size_t len) noexcept {
    if (::RAND_bytes(buf, len) != 1) {
        abort();
    }
}

}
