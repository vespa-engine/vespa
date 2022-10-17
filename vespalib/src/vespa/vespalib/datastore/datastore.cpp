// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "datastore.hpp"
#include <vespa/vespalib/util/rcuvector.hpp>

namespace vespalib::datastore {

template class DataStoreT<EntryRefT<22> >;

}

template class vespalib::RcuVector<vespalib::datastore::EntryRef>;
template class vespalib::RcuVectorBase<vespalib::datastore::EntryRef>;
template class vespalib::RcuVector<vespalib::datastore::AtomicEntryRef>;
template class vespalib::RcuVectorBase<vespalib::datastore::AtomicEntryRef>;
