// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "fixed_bucket_spaces.h"

namespace document {

VESPA_IMPLEMENT_EXCEPTION(UnknownBucketSpaceException, vespalib::IllegalArgumentException)

// Some sanity checks to ensure we don't mess up any legacy mappings.
static_assert(BucketSpace::placeHolder() != BucketSpace::invalid());
static_assert(FixedBucketSpaces::default_space() == BucketSpace::placeHolder());
static_assert(FixedBucketSpaces::global_space() != FixedBucketSpaces::default_space());

namespace {

vespalib::string DEFAULT = "default";
vespalib::string GLOBAL = "global";

}

BucketSpace FixedBucketSpaces::from_string(std::string_view name) {
    if (name == DEFAULT) {
        return default_space();
    } else if (name == GLOBAL) {
        return global_space();
    } else {
        throw UnknownBucketSpaceException("Unknown bucket space name: " + vespalib::string(name), VESPA_STRLOC);
    }
}

const vespalib::string &
FixedBucketSpaces::to_string(BucketSpace space) {
    if (space == default_space()) {
        return DEFAULT;
    } else if (space == global_space()) {
        return GLOBAL;
    } else {
        throw UnknownBucketSpaceException("Unknown bucket space: " + space.toString(), VESPA_STRLOC);
    }
}

}
