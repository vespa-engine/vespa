// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once
#include <cstddef>

namespace vespalib::crypto {

// Fills `buf` with `len` bytes of cryptographically secure pseudo-random data.
// Aborts the process if CSPRNG somehow fails.
void random_buffer(unsigned char* buf, size_t len) noexcept;

}
