// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "element_ids.h"

namespace search::common {

namespace {

const uint32_t none{0};

}

const std::span<const uint32_t> ElementIds::_empty{&none, 0};

}
