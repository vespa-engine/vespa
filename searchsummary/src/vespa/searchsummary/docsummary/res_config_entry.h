// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "res_type.h"
#include <vespa/vespalib/stllike/string.h>

namespace search::docsummary {

/**
 * This struct describes a single docsum field (name and type).
 **/
struct ResConfigEntry {
    ResType          _type;
    vespalib::string _bindname;
    int              _enumValue;
    ResConfigEntry() noexcept;
    ~ResConfigEntry();
};

}
