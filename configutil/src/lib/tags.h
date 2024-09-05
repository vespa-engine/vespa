// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <string>

namespace configdefinitions {

std::string upcase(const std::string &orig);
bool tagsContain(const std::string &tags, const std::string &tag);

}

