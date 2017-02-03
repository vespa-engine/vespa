// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "unique_store.hpp"
#include <vespa/document/base/globalid.h>

namespace search {
namespace datastore {

template class UniqueStore<document::GlobalId, EntryRefT<22>>;
template class UniqueStoreBuilder<document::GlobalId, EntryRefT<22>>;
template class UniqueStoreSaver<document::GlobalId, EntryRefT<22>>;

} // namespace datastore
} // namespace search
