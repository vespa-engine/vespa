// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/check_require.h>
#include <vespa/vespalib/data/slime/slime.h>

namespace search::docsummary::test {

/**
 * Utility class that wraps a slime object generated from json.
 */
struct SlimeValue {
    vespalib::Slime slime;

    SlimeValue(const std::string& json_input) : slime() {
        size_t used = vespalib::slime::JsonFormat::decode(json_input, slime);
        CHECK(used > 0);
    }
};

} // namespace search::docsummary::test
