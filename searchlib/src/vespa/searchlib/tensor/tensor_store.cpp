// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_store.h"
#include <vespa/searchlib/datastore/datastore.hpp>

namespace search {

namespace tensor {

constexpr size_t MIN_BUFFER_CLUSTERS = 1024;

TensorStore::TensorStore(datastore::DataStoreBase &store)
    : _store(store),
      _typeId(0)
{
}

TensorStore::~TensorStore()
{
}

}  // namespace search::tensor

}  // namespace search
