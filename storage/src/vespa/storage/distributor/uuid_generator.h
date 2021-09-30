// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace storage::distributor {

/**
 * Generator of universally unique identifiers (not actually conforming UUIDs, as these
 * have MSB semantics) that are expected to be effectively collision free.
 */
class UuidGenerator {
public:
    virtual ~UuidGenerator() = default;
    // Returns a string that is guaranteed ASCII-only
    virtual vespalib::string generate_uuid() const = 0;
};

}
