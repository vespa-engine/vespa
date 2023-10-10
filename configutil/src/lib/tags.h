// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace configdefinitions {

vespalib::string upcase(const vespalib::string &orig);
bool tagsContain(const vespalib::string &tags, const vespalib::string &tag);

}

