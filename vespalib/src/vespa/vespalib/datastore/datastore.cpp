// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "datastore.h"
#include "datastore.hpp"
#include <vespa/vespalib/util/array.hpp>
#include <vespa/vespalib/util/rcuvector.hpp>

namespace vespalib::datastore {

template class DataStoreT<EntryRefT<22> >;

}

template void vespalib::Array<vespalib::datastore::DataStoreBase::ElemHold1ListElem>::increase(size_t);
template class vespalib::RcuVector<vespalib::datastore::EntryRef>;
template class vespalib::RcuVectorBase<vespalib::datastore::EntryRef>;
//template void vespalib::RcuVectorBase<vespalib::datastore::EntryRef>::expandAndInsert(const vespalib::datastore::EntryRef &);
