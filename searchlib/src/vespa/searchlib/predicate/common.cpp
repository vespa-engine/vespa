// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "common.h"
#include "predicate_hash.h"

namespace search::predicate {

const vespalib::string Constants::z_star_attribute_name = "z-star";
const uint64_t Constants::z_star_hash = PredicateHash::hash64(Constants::z_star_attribute_name);
const vespalib::string Constants::z_star_compressed_attribute_name = "z-star-compressed";
const uint64_t Constants::z_star_compressed_hash = PredicateHash::hash64(Constants::z_star_compressed_attribute_name);

}
