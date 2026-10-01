// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "datastore.hpp"

#include <vespa/vespalib/util/type_stable_vector.hpp>

namespace vespalib::datastore {

template class DataStoreT<EntryRefT<22>>;

}

template class vespalib::TypeStableVector<vespalib::datastore::EntryRef>;
template class vespalib::TypeStableVectorBase<vespalib::datastore::EntryRef>;
template class vespalib::TypeStableVector<vespalib::datastore::AtomicEntryRef>;
template class vespalib::TypeStableVectorBase<vespalib::datastore::AtomicEntryRef>;
