// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "datastore.h"
#include "datastore.hpp"
#include <vespa/vespalib/util/array.hpp>
#include <vespa/searchlib/common/rcuvector.hpp>

namespace search {
namespace datastore {

template class DataStoreT<EntryRefT<22> >;

} // namespace datastore
} // namespace search

template void vespalib::Array<search::datastore::DataStoreBase::ElemHold1ListElem>::increase(size_t);
template class search::attribute::RcuVector<search::datastore::EntryRef>;
template class search::attribute::RcuVectorBase<search::datastore::EntryRef>;
//template void search::attribute::RcuVectorBase<search::datastore::EntryRef>::expandAndInsert(const search::datastore::EntryRef &);
