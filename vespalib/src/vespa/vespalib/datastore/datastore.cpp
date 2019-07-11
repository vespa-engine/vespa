// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "datastore.h"
#include "datastore.hpp"
#include <vespa/vespalib/util/array.hpp>
#include <vespa/vespalib/util/rcuvector.hpp>

namespace search::datastore {

template class DataStoreT<EntryRefT<22> >;

}

template void vespalib::Array<search::datastore::DataStoreBase::ElemHold1ListElem>::increase(size_t);
template class vespalib::RcuVector<search::datastore::EntryRef>;
template class vespalib::RcuVectorBase<search::datastore::EntryRef>;
//template void vespalib::RcuVectorBase<search::datastore::EntryRef>::expandAndInsert(const search::datastore::EntryRef &);
