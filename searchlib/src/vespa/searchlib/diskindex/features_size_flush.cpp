// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "features_size_flush.h"
#include <vespa/searchlib/index/postinglistparams.h>

using search::index::PostingListParams;

namespace search::diskindex {

void
setup_default_features_size_flush(PostingListParams& params)
{
    if (force_features_size_flush_always) {
        params.set(tags::FEATURES_SIZE_FLUSH_BITS, 2);
    }
}

}

namespace search::diskindex::tags {

std::string FEATURES_SIZE_FLUSH_BITS("features_size_flush_bits");

}
