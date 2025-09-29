// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "target_info.h"
#include <format>

namespace vespalib::hwaccelerated {

std::string TargetInfo::to_string() const {
    return std::format("{} - {} ({} bit vector width)", implementation_name(), target_name(), vector_width_bits());
}

} // vespalib::hwaccelerated
