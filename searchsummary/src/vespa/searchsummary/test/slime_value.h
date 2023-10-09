// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/data/slime/slime.h>
#include <cassert>

namespace search::docsummary::test {

/**
 * Utility class that wraps a slime object generated from json.
 */
struct SlimeValue {
    vespalib::Slime slime;

    SlimeValue(const vespalib::string& json_input)
        : slime()
    {
        size_t used = vespalib::slime::JsonFormat::decode(json_input, slime);
        assert(used > 0);
    }
};

}
