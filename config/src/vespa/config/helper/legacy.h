// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <string>
#include <vespa/config/subscription/sourcespec.h>

namespace config {

bool isLegacyConfigId(std::string_view configId);
std::unique_ptr<SourceSpec> legacyConfigId2Spec(std::string_view configId);
const std::string legacyConfigId2ConfigId(std::string_view configId);

}


