// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "fixed_bucket_spaces.h"

namespace document {

VESPA_IMPLEMENT_EXCEPTION(UnknownBucketSpaceException, vespalib::IllegalArgumentException)

// Some sanity checks to ensure we don't mess up any legacy mappings.
static_assert(BucketSpace::placeHolder() != BucketSpace::invalid());
static_assert(FixedBucketSpaces::default_space() == BucketSpace::placeHolder());
static_assert(FixedBucketSpaces::global_space() != FixedBucketSpaces::default_space());

BucketSpace FixedBucketSpaces::from_string(vespalib::stringref name) {
    if (name == "default") {
        return default_space();
    } else if (name == "global") {
        return global_space();
    } else {
        throw UnknownBucketSpaceException("Unknown bucket space name: " + vespalib::string(name), VESPA_STRLOC);
    }
}

vespalib::stringref FixedBucketSpaces::to_string(BucketSpace space) {
    if (space == default_space()) {
        return "default";
    } else if (space == global_space()) {
        return "global";
    } else {
        throw UnknownBucketSpaceException("Unknown bucket space: " + space.toString(), VESPA_STRLOC);
    }
}

}
