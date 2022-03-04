// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "unique_store.hpp"
#include "buffer_type.hpp"

namespace vespalib::datastore {

using namespace btree;

VESPALIB_DATASTORE_INSTANTIATE_BUFFERTYPE_INTERNALNODE(AtomicEntryRef, NoAggregated, uniquestore::DefaultDictionaryTraits::INTERNAL_SLOTS);
VESPALIB_DATASTORE_INSTANTIATE_BUFFERTYPE_LEAFNODE(AtomicEntryRef, BTreeNoLeafData, NoAggregated, uniquestore::DefaultDictionaryTraits::LEAF_SLOTS);

}
