// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "reindexing_constants.h"

namespace storage {

const char* reindexing_bucket_lock_bypass_prefix() noexcept {
    // This is by design a string that will fail to parse as a valid document selection in the backend.
    // It's only used to bypass the read-for-write visitor bucket lock.
    return "@@__vespa_internal_allow_through_bucket_lock";
}

const char* reindexing_bucket_lock_visitor_parameter_key() noexcept {
    return "__vespa_internal_reindexing_bucket_token";
}

}
