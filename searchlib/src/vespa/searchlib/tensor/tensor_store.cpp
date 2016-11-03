// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "tensor_store.h"
#include <vespa/searchlib/datastore/datastore.hpp>

namespace search {

namespace attribute {

constexpr size_t MIN_BUFFER_CLUSTERS = 1024;

TensorStore::TensorStore(btree::DataStoreBase &store)
    : _store(store),
      _typeId(0)
{
}

TensorStore::~TensorStore()
{
}

}  // namespace search::attribute

}  // namespace search
