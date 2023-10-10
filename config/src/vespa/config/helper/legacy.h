// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <string>
#include <vespa/config/subscription/sourcespec.h>

namespace config {

bool isLegacyConfigId(const std::string & configId);
std::unique_ptr<SourceSpec> legacyConfigId2Spec(const std::string & configId);
const std::string legacyConfigId2ConfigId(const std::string & configId);

}


