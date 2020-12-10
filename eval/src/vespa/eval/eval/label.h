// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace vespalib::eval {

// We use string ids from SharedStringRepo as labels. Note that
// label_t represents the lightweight reference type. Other structures
// (Handle/StrongHandles) are needed to keep the id valid.

using label_t = uint32_t;

}
