// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/bucket/bucketspace.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/stllike/string.h>

namespace storage::spi {

VESPA_DEFINE_EXCEPTION(UnknownBucketSpaceException, vespalib::IllegalArgumentException);

/**
 * Minimal repository/factory of bucket spaces hard coded for default and global
 * distributions.
 */
struct FixedBucketSpaces {
    static constexpr document::BucketSpace default_space() { return document::BucketSpace(1); };
    static constexpr document::BucketSpace global_space() { return document::BucketSpace(2); }

    // Post-condition: returned space has valid() == true iff name
    // is either "default" or "global".
    // Throws UnknownBucketSpaceException if name does not map to a known bucket space.
    static document::BucketSpace from_string(vespalib::stringref name);
    // Post-condition: returned string can be losslessly passed to from_string()
    // iff space is equal to default_space() or global_space().
    // Throws UnknownBucketSpaceException if space does not map to a known name.
    static vespalib::stringref to_string(document::BucketSpace space);
};

}
