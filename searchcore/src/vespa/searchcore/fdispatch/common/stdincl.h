// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

/**
 * This method defines the illegal/undefined value for unsigned 32-bit
 * integer ids.
 **/
inline uint32_t FastS_NoID32() { return static_cast<uint32_t>(-1); }

