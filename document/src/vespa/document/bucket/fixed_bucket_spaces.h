// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "bucketspace.h"
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/stllike/string.h>

namespace document {

VESPA_DEFINE_EXCEPTION(UnknownBucketSpaceException, vespalib::IllegalArgumentException);

/**
 * Minimal repository/factory of bucket spaces hard coded for default and global
 * distributions.
 */
struct FixedBucketSpaces {
    static constexpr BucketSpace default_space() { return BucketSpace(1); };
    static constexpr BucketSpace global_space() { return BucketSpace(2); }

    // Post-condition: returned space has valid() == true iff name
    // is either "default" or "global".
    // Throws UnknownBucketSpaceException if name does not map to a known bucket space.
    static BucketSpace from_string(vespalib::stringref name);
    // Post-condition: returned string can be losslessly passed to from_string()
    // iff space is equal to default_space() or global_space().
    // Throws UnknownBucketSpaceException if space does not map to a known name.
    static vespalib::stringref to_string(BucketSpace space);
};

}
