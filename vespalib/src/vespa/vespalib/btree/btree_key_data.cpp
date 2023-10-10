// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "btree_key_data.h"

namespace vespalib::btree {

BTreeNoLeafData BTreeNoLeafData::_instance;

template class BTreeKeyData<uint32_t, uint32_t>;
template class BTreeKeyData<uint32_t, int32_t>;

} // namespace vespalib::btree
