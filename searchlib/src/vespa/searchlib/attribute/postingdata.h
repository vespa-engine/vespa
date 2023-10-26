// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/btree/btree_key_data.h>

namespace search {

using AttributePosting = vespalib::btree::BTreeKeyData<uint32_t, vespalib::btree::BTreeNoLeafData>;
using AttributeWeightPosting = vespalib::btree::BTreeKeyData<uint32_t, int32_t>;

}

