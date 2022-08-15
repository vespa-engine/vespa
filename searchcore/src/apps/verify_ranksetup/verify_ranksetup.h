// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vector>

std::pair<bool, std::vector<vespalib::string>> verifyRankSetup(const char * configId);
